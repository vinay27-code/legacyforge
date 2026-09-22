package com.legacyforge.planning.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legacyforge.ingestion.entity.Repo;
import com.legacyforge.ingestion.entity.RepoFile;
import com.legacyforge.ingestion.repo.RepoFileRepository;
import com.legacyforge.planning.dto.PlanDtos;
import com.legacyforge.planning.entity.MigrationPlan;
import com.legacyforge.planning.repo.MigrationPlanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates a phased migration plan for a repo by handing the whole file
 * inventory + detected framework fingerprints to an LLM and asking for
 * structured JSON back. Gemini 2.5 Flash's 1M context comfortably fits a
 * small legacy monolith like jpetstore; for larger repos we would summarise
 * before prompting.
 */
@Service
public class PlanningService {

    private static final Logger log = LoggerFactory.getLogger(PlanningService.class);

    private final LlmProvider llm;
    private final RepoFileRepository files;
    private final MigrationPlanRepository plans;
    private final ObjectMapper json;

    public PlanningService(LlmProvider llm, RepoFileRepository files,
                           MigrationPlanRepository plans, ObjectMapper json) {
        this.llm = llm;
        this.files = files;
        this.plans = plans;
        this.json = json;
    }

    @Transactional
    public MigrationPlan generate(Repo repo) {
        List<RepoFile> all = files.findAll().stream()
                .filter(f -> f.getRepoId().equals(repo.getId()))
                .toList();

        String userPrompt = buildUserPrompt(repo, all);
        log.info("Planning prompt for repo {}: {} chars, provider={}",
                repo.getId(), userPrompt.length(), llm.describe());

        LlmProvider.LlmResponse resp = llm.completeJson(SYSTEM_PROMPT, userPrompt);
        String content = stripFences(resp.content());

        // Validate parseable before we save
        try {
            json.readTree(content);
        } catch (Exception e) {
            log.error("LLM returned invalid JSON: {}", content.substring(0, Math.min(400, content.length())));
            throw new RuntimeException("LLM returned invalid JSON: " + e.getMessage(), e);
        }

        plans.deleteByRepoId(repo.getId());
        plans.flush();

        MigrationPlan mp = new MigrationPlan();
        mp.setRepoId(repo.getId());
        mp.setProvider(llm.describe());
        mp.setPlanJson(content);
        mp.setPromptTokens(resp.promptTokens());
        mp.setOutputTokens(resp.outputTokens());
        return plans.save(mp);
    }

    public Optional<MigrationPlan> find(Repo repo) {
        return plans.findByRepoId(repo.getId());
    }

    public PlanDtos.PlanResponse toResponse(MigrationPlan mp) {
        PlanDtos.PlanContent content;
        try {
            content = json.readValue(mp.getPlanJson(), PlanDtos.PlanContent.class);
        } catch (Exception e) {
            throw new RuntimeException("Stored plan JSON no longer parses: " + e.getMessage(), e);
        }
        return new PlanDtos.PlanResponse(
                mp.getId(),
                mp.getRepoId(),
                mp.getProvider(),
                mp.getGeneratedAt(),
                mp.getPromptTokens(),
                mp.getOutputTokens(),
                content
        );
    }

    // ---- prompt building -------------------------------------------------

    private String buildUserPrompt(Repo repo, List<RepoFile> all) {
        Fingerprints fp = detectFingerprints(all);

        long totalBytes = all.stream()
                .filter(f -> !f.isBinary() && f.getContent() != null)
                .mapToLong(f -> f.getContent().length()).sum();

        Map<String, Long> byLang = all.stream()
                .filter(f -> f.getLanguage() != null)
                .collect(Collectors.groupingBy(RepoFile::getLanguage, Collectors.counting()));

        StringBuilder p = new StringBuilder();
        p.append("Repository: ").append(repo.getName()).append('\n');
        p.append("Total files: ").append(all.size()).append('\n');
        p.append("Total source bytes: ").append(totalBytes).append('\n');
        p.append("Languages: ").append(byLang.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", "))).append("\n\n");

        p.append("Detected framework fingerprints (legacy stack we are migrating AWAY from):\n");
        p.append("- Struts 1.x: ").append(fp.struts ? "YES" : "no").append('\n');
        p.append("- iBATIS / MyBatis: ").append(fp.ibatis ? "YES" : "no").append('\n');
        p.append("- JSP views: ").append(fp.jspCount).append(" files\n");
        p.append("- web.xml deployment descriptor: ").append(fp.webXml ? "YES" : "no").append('\n');
        p.append("- Ant/Maven build: ").append(fp.build).append('\n');
        p.append("- Servlet API: ").append(fp.servlets ? "YES" : "no").append("\n\n");

        p.append("Target modern stack: Spring Boot 3 (Java 21) backend + Angular 18 frontend + Postgres.\n\n");

        p.append("Full file inventory (path | language | bytes):\n");
        for (RepoFile f : all) {
            int size = (f.getContent() == null) ? 0 : f.getContent().length();
            p.append("- ").append(f.getPath())
                    .append(" | ").append(f.getLanguage() == null ? "" : f.getLanguage())
                    .append(" | ").append(size)
                    .append('\n');
        }

        p.append("\nProduce a phased migration plan as JSON matching the schema in the system prompt. ")
                .append("Every file listed above must appear in exactly one phase's `files` array. ")
                .append("Order phases from foundational (build, config, security) through data + services to UI (JSP → Angular). ")
                .append("Set risk based on framework coupling and file complexity, not just size. ")
                .append("Be concrete in `notes`: name the target framework class or pattern.");

        return p.toString();
    }

    private static final String SYSTEM_PROMPT = """
            You are a senior Java migration architect. Given the current state of a
            legacy Java monolith, produce a phased migration plan to Spring Boot 3
            (Java 21) + Angular 18 + Postgres.

            Return ONLY a valid JSON object. No prose, no code fences. The object
            MUST match this schema exactly:

            {
              "summary": "1-2 paragraph executive summary of the migration effort",
              "overallRisk": "LOW | MEDIUM | HIGH",
              "totalEstimatedDays": <integer, sum of phase days>,
              "phases": [
                {
                  "phaseNumber": <1-based integer>,
                  "title": "Short phase title",
                  "description": "What this phase accomplishes and why it comes now",
                  "estimatedDays": <integer>,
                  "files": [
                    {
                      "path": "<exact file path from the input>",
                      "risk": "LOW | MEDIUM | HIGH",
                      "reason": "Why this risk level (framework coupling, complexity, business criticality)",
                      "notes": "Concrete migration approach: target Spring/Angular class, pattern, or library"
                    }
                  ]
                }
              ]
            }

            Rules:
            - 3 to 6 phases total. Prefer 4-5.
            - Every input file must appear in exactly one phase.
            - Phases must be ordered by dependency: build/config first, then persistence
              and domain, then services and controllers, then views (JSP → Angular last).
            - Risk factors: Struts action classes = HIGH, iBATIS SQL maps = MEDIUM/HIGH,
              JSPs with scriptlets = HIGH, pojos/DTOs = LOW, config = LOW.
            - `notes` must name a concrete target (e.g. "Spring @RestController",
              "MyBatis 3.x @Mapper with XML kept as-is", "Angular standalone component").
            """;

    private static String stripFences(String s) {
        String t = s.strip();
        if (t.startsWith("```")) {
            int firstNl = t.indexOf('\n');
            if (firstNl > 0) t = t.substring(firstNl + 1);
            if (t.endsWith("```")) t = t.substring(0, t.length() - 3);
        }
        return t.strip();
    }

    // ---- fingerprint detection ------------------------------------------

    private record Fingerprints(boolean struts, boolean ibatis, int jspCount,
                                boolean webXml, String build, boolean servlets) {}

    private Fingerprints detectFingerprints(List<RepoFile> all) {
        boolean struts = false, ibatis = false, webXml = false, servlets = false;
        int jsps = 0;
        String build = "unknown";

        for (RepoFile f : all) {
            String p = f.getPath().toLowerCase();
            String content = (f.getContent() == null) ? "" : f.getContent().toLowerCase();

            if (p.endsWith("struts-config.xml") || content.contains("org.apache.struts.action")) struts = true;
            if (p.endsWith(".xml") && (content.contains("<!doctype sqlmap") || content.contains("mybatis"))) ibatis = true;
            if (p.contains("ibatis") || p.contains("mybatis")) ibatis = true;
            if (p.endsWith(".jsp")) jsps++;
            if (p.endsWith("web.xml")) webXml = true;
            if (content.contains("javax.servlet") || content.contains("jakarta.servlet")) servlets = true;
            if (p.endsWith("pom.xml")) build = "Maven (pom.xml)";
            else if (p.endsWith("build.xml") && !"Maven (pom.xml)".equals(build)) build = "Ant (build.xml)";
            else if ((p.endsWith("build.gradle") || p.endsWith("build.gradle.kts")) && "unknown".equals(build)) build = "Gradle";
        }
        return new Fingerprints(struts, ibatis, jsps, webXml, build, servlets);
    }
}
