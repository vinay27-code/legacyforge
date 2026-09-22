package com.legacyforge.agents.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legacyforge.agents.entity.MigrationArtifact;
import com.legacyforge.agents.repo.MigrationArtifactRepository;
import com.legacyforge.ingestion.entity.Repo;
import com.legacyforge.ingestion.entity.RepoFile;
import com.legacyforge.ingestion.repo.RepoFileRepository;
import com.legacyforge.planning.entity.MigrationPlan;
import com.legacyforge.planning.repo.MigrationPlanRepository;
import com.legacyforge.planning.service.LlmProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Orchestrates per-file migration agents. Given a repo's stored plan, spawns
 * one LLM call per file (in parallel, capped by a small pool so we don't
 * hammer the provider) and stores each generated artifact.
 */
@Service
public class MigrationAgentService {

    private static final Logger log = LoggerFactory.getLogger(MigrationAgentService.class);

    private static final int PARALLELISM = 6;
    private static final int MAX_ORIGINAL_CHARS = 12000; // cap prompt size per file

    private final LlmProvider llm;
    private final MigrationPlanRepository plans;
    private final MigrationArtifactRepository artifacts;
    private final RepoFileRepository files;
    private final ObjectMapper json;

    public MigrationAgentService(LlmProvider llm,
                                 MigrationPlanRepository plans,
                                 MigrationArtifactRepository artifacts,
                                 RepoFileRepository files,
                                 ObjectMapper json) {
        this.llm = llm;
        this.plans = plans;
        this.artifacts = artifacts;
        this.files = files;
        this.json = json;
    }

    /**
     * Delete any previous artifacts and generate a fresh set for every file
     * the plan touches. Runs each file's LLM call in parallel across a small
     * pool. Returns once every file is either SUCCESS or FAILED.
     */
    @Transactional
    public List<MigrationArtifact> runAgainstPlan(Repo repo) {
        MigrationPlan plan = plans.findByRepoId(repo.getId())
                .orElseThrow(() -> new IllegalStateException("No plan yet — generate one first."));

        Map<String, RepoFile> byPath = files.findAll().stream()
                .filter(f -> f.getRepoId().equals(repo.getId()))
                .collect(Collectors.toMap(RepoFile::getPath, f -> f, (a, b) -> a));

        JsonNode root;
        try {
            root = json.readTree(plan.getPlanJson());
        } catch (Exception e) {
            throw new RuntimeException("Stored plan is not valid JSON: " + e.getMessage(), e);
        }

        artifacts.deleteByRepoId(repo.getId());
        artifacts.flush();

        // Materialise every planned file as a PENDING artifact first
        List<MigrationArtifact> pending = new ArrayList<>();
        for (JsonNode phase : root.path("phases")) {
            int phaseNumber = phase.path("phaseNumber").asInt();
            String phaseTitle = phase.path("title").asText("Phase " + phaseNumber);
            for (JsonNode file : phase.path("files")) {
                String path = file.path("path").asText();
                RepoFile source = byPath.get(path);
                MigrationArtifact a = new MigrationArtifact();
                a.setPlanId(plan.getId());
                a.setRepoId(repo.getId());
                a.setFilePath(path);
                a.setPhaseNumber(phaseNumber);
                a.setPhaseTitle(phaseTitle);
                a.setRisk(file.path("risk").asText("MEDIUM"));
                a.setStatus(MigrationArtifact.Status.PENDING);
                a.setOriginalCode(source != null && source.getContent() != null
                        ? trim(source.getContent()) : null);
                pending.add(a);
            }
        }
        List<MigrationArtifact> saved = artifacts.saveAll(pending);
        artifacts.flush();

        // Run all files in parallel through a small executor
        ExecutorService pool = Executors.newFixedThreadPool(PARALLELISM);
        try {
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (MigrationArtifact a : saved) {
                JsonNode phase = findPhase(root, a.getPhaseNumber());
                JsonNode fileEntry = findFileInPhase(phase, a.getFilePath());
                String notes = fileEntry != null ? fileEntry.path("notes").asText("") : "";
                String reason = fileEntry != null ? fileEntry.path("reason").asText("") : "";
                futures.add(CompletableFuture.runAsync(() -> processOne(a, phase, notes, reason), pool));
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } finally {
            pool.shutdown();
            try { pool.awaitTermination(30, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        }

        return artifacts.findByRepoIdOrderByPhaseNumberAscFilePathAsc(repo.getId());
    }

    private void processOne(MigrationArtifact a, JsonNode phase, String notes, String reason) {
        a.setStartedAt(Instant.now());
        a.setStatus(MigrationArtifact.Status.RUNNING);
        artifacts.saveAndFlush(a);

        try {
            if (a.getOriginalCode() == null || a.getOriginalCode().isBlank()) {
                throw new IllegalStateException("No source content available for " + a.getFilePath());
            }
            String userPrompt = buildUserPrompt(a, phase, notes, reason);
            LlmProvider.LlmResponse resp = llm.completeText(SYSTEM_PROMPT, userPrompt);
            String content = stripFences(resp.content());
            String targetPath = extractTargetPath(content);
            String generated = stripTargetHeader(content);

            a.setGeneratedCode(generated);
            a.setTargetPath(targetPath);
            a.setPromptTokens(resp.promptTokens());
            a.setOutputTokens(resp.outputTokens());
            a.setStatus(MigrationArtifact.Status.SUCCESS);
            a.setCompletedAt(Instant.now());
            artifacts.saveAndFlush(a);
            log.debug("Migrated {} → {}", a.getFilePath(), targetPath);
        } catch (Exception e) {
            a.setStatus(MigrationArtifact.Status.FAILED);
            a.setErrorMessage(e.getMessage());
            a.setCompletedAt(Instant.now());
            artifacts.saveAndFlush(a);
            log.warn("Agent failed on {}: {}", a.getFilePath(), e.getMessage());
        }
    }

    public List<MigrationArtifact> list(Repo repo) {
        return artifacts.findByRepoIdOrderByPhaseNumberAscFilePathAsc(repo.getId());
    }

    public Optional<MigrationArtifact> detail(UUID artifactId) {
        return artifacts.findById(artifactId);
    }

    // ---- prompt --------------------------------------------------------

    private String buildUserPrompt(MigrationArtifact a, JsonNode phase, String notes, String reason) {
        StringBuilder p = new StringBuilder();
        p.append("Phase ").append(a.getPhaseNumber()).append(": ").append(a.getPhaseTitle()).append('\n');
        if (phase != null && !phase.path("description").isMissingNode()) {
            p.append("Phase description: ").append(phase.path("description").asText()).append('\n');
        }
        p.append("Legacy file: ").append(a.getFilePath()).append('\n');
        p.append("Risk: ").append(a.getRisk());
        if (!reason.isBlank()) p.append(" (").append(reason).append(')');
        p.append('\n');
        if (!notes.isBlank()) p.append("Migration approach from plan: ").append(notes).append('\n');
        p.append("\nLegacy source:\n---\n").append(a.getOriginalCode()).append("\n---\n\n");
        p.append("Produce the modernized equivalent. Follow the rules in the system prompt exactly.");
        return p.toString();
    }

    private static final String SYSTEM_PROMPT = """
            You are a senior Java migration engineer. Given a single legacy source
            file and a target modernization approach, produce the complete
            modernized equivalent for Spring Boot 3 (Java 21) + Angular 18.

            Output rules — follow exactly:
            1. Return ONLY the source code. No prose, no explanation, no markdown code fences.
            2. The FIRST line MUST be a comment naming the target path:
                 // TARGET: <full path under src/main/java/... or src/app/...>
            3. Then the code, ready to compile / run.
            4. Preserve business logic, field names, and behavior. Change framework mechanics only.
            5. Use Java 21 features (records, pattern matching, var, switch expressions) where they fit.
            6. Java classes: @RestController with @GetMapping/@PostMapping and request bodies;
               @Service with constructor injection; JPA @Entity with @Id and @GeneratedValue.
            7. JSPs: convert to a single Angular 18 standalone component (.ts) with an inline template
               and inline styles. Use Angular Material or plain HTML — no external template files.
            8. iBATIS/MyBatis mappers: convert to Spring Data JPA @Repository interfaces when the
               query is simple; keep @Mapper for complex SQL that doesn't map cleanly.
            9. Config XML: convert to @Configuration classes or application.yml. If the target is
               application.yml, wrap the yml in the file with a preceding comment.
            10. Never emit TODOs, placeholders, or "// implement this". Write real code.
            """;

    // ---- helpers -------------------------------------------------------

    private JsonNode findPhase(JsonNode root, int phaseNumber) {
        for (JsonNode phase : root.path("phases")) {
            if (phase.path("phaseNumber").asInt() == phaseNumber) return phase;
        }
        return null;
    }

    private JsonNode findFileInPhase(JsonNode phase, String path) {
        if (phase == null) return null;
        for (JsonNode f : phase.path("files")) {
            if (path.equals(f.path("path").asText())) return f;
        }
        return null;
    }

    private static String trim(String s) {
        if (s.length() <= MAX_ORIGINAL_CHARS) return s;
        return s.substring(0, MAX_ORIGINAL_CHARS) + "\n// ... (truncated at " + MAX_ORIGINAL_CHARS + " chars)";
    }

    private static String stripFences(String s) {
        String t = s.strip();
        if (t.startsWith("```")) {
            int firstNl = t.indexOf('\n');
            if (firstNl > 0) t = t.substring(firstNl + 1);
            if (t.endsWith("```")) t = t.substring(0, t.length() - 3);
        }
        return t.strip();
    }

    private static final Pattern TARGET_LINE =
            Pattern.compile("^\\s*//\\s*TARGET:\\s*(.+?)\\s*$", Pattern.MULTILINE);

    private static String extractTargetPath(String generated) {
        Matcher m = TARGET_LINE.matcher(generated);
        if (m.find()) return m.group(1).trim();
        return null;
    }

    private static String stripTargetHeader(String generated) {
        Matcher m = TARGET_LINE.matcher(generated);
        if (m.find()) {
            String rest = generated.substring(m.end()).replaceFirst("^\\s*\\n", "");
            return rest;
        }
        return generated;
    }
}
