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

@Service
public class MigrationAgentService {

    private static final Logger log = LoggerFactory.getLogger(MigrationAgentService.class);

    private static final int PARALLELISM = 6;
    private static final int MAX_ORIGINAL_CHARS = 12000;
    private static final int MAX_RETRIES = 2;

    private final LlmProvider llm;
    private final MigrationPlanRepository plans;
    private final MigrationArtifactRepository artifacts;
    private final RepoFileRepository files;
    private final ValidationService validator;
    private final DependencyAnalyzer dependencyAnalyzer;
    private final ObjectMapper json;

    public MigrationAgentService(LlmProvider llm,
                                 MigrationPlanRepository plans,
                                 MigrationArtifactRepository artifacts,
                                 RepoFileRepository files,
                                 ValidationService validator,
                                 DependencyAnalyzer dependencyAnalyzer,
                                 ObjectMapper json) {
        this.llm = llm;
        this.plans = plans;
        this.artifacts = artifacts;
        this.files = files;
        this.validator = validator;
        this.dependencyAnalyzer = dependencyAnalyzer;
        this.json = json;
    }

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

        List<MigrationArtifact> pending = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode phase : root.path("phases")) {
            int phaseNumber = phase.path("phaseNumber").asInt();
            String phaseTitle = phase.path("title").asText("Phase " + phaseNumber);
            for (JsonNode file : phase.path("files")) {
                String path = file.path("path").asText();
                if (!seen.add(phaseNumber + "|" + path)) continue;
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

        // After all agents finish (successes + derived siblings), rebuild dep graph.
        try {
            dependencyAnalyzer.rebuildFor(repo.getId());
        } catch (Exception e) {
            log.warn("Dependency graph rebuild failed: {}", e.getMessage());
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
            List<Split> splits = splitByTargetHeaders(content);

            int totalIn = resp.promptTokens() != null ? resp.promptTokens() : 0;
            int totalOut = resp.outputTokens() != null ? resp.outputTokens() : 0;

            if (splits.isEmpty()) {
                // No TARGET header — treat entire content as a single unit with no target
                splits = List.of(new Split(null, content));
            }

            // Primary split lives on the original artifact.
            Split primary = splits.get(0);
            String generated = primary.code;
            String targetPath = primary.targetPath;

            ValidationService.Result validation = validator.validate(targetPath, generated);
            int retries = 0;
            while (validation.status() == ValidationService.Result.Status.INVALID && retries < MAX_RETRIES) {
                retries++;
                String retryPrompt = buildRetryPrompt(userPrompt, generated, validation.errors());
                LlmProvider.LlmResponse retryResp = llm.completeText(SYSTEM_PROMPT, retryPrompt);
                String retryContent = stripFences(retryResp.content());
                List<Split> retrySplits = splitByTargetHeaders(retryContent);
                if (retrySplits.isEmpty()) retrySplits = List.of(new Split(null, retryContent));
                Split rp = retrySplits.get(0);
                targetPath = rp.targetPath != null ? rp.targetPath : targetPath;
                generated = rp.code;
                totalIn += retryResp.promptTokens() != null ? retryResp.promptTokens() : 0;
                totalOut += retryResp.outputTokens() != null ? retryResp.outputTokens() : 0;
                validation = validator.validate(targetPath, generated);
                // Also refresh splits so any additional TARGETs in the retry replace the previous set
                splits = retrySplits;
            }

            a.setGeneratedCode(generated);
            a.setTargetPath(targetPath);
            a.setPromptTokens(totalIn);
            a.setOutputTokens(totalOut);
            a.setRetryCount(retries);
            a.setValidationStatus(toEntityStatus(validation.status()));
            a.setValidationErrors(validation.errors().isEmpty() ? null
                    : String.join("\n", validation.errors()));
            a.setStatus(MigrationArtifact.Status.SUCCESS);
            a.setCompletedAt(Instant.now());
            artifacts.saveAndFlush(a);

            // Additional splits become sibling artifacts under the same phase, parented to primary.
            for (int i = 1; i < splits.size(); i++) {
                Split s = splits.get(i);
                MigrationArtifact child = new MigrationArtifact();
                child.setPlanId(a.getPlanId());
                child.setRepoId(a.getRepoId());
                child.setParentArtifactId(a.getId());
                child.setFilePath(a.getFilePath() + " (derived " + i + ")");
                child.setTargetPath(s.targetPath);
                child.setPhaseNumber(a.getPhaseNumber());
                child.setPhaseTitle(a.getPhaseTitle());
                child.setRisk(a.getRisk());
                child.setStatus(MigrationArtifact.Status.SUCCESS);
                child.setGeneratedCode(s.code);
                child.setOriginalCode(null); // no separate legacy source; it's a derivative of the primary
                ValidationService.Result cv = validator.validate(s.targetPath, s.code);
                child.setValidationStatus(toEntityStatus(cv.status()));
                child.setValidationErrors(cv.errors().isEmpty() ? null : String.join("\n", cv.errors()));
                child.setRetryCount(0);
                child.setStartedAt(a.getStartedAt());
                child.setCompletedAt(Instant.now());
                artifacts.saveAndFlush(child);
            }

            log.debug("Migrated {} → {} [{}, retries={}, siblings={}]",
                    a.getFilePath(), targetPath, validation.status(), retries, splits.size() - 1);
        } catch (Exception e) {
            a.setStatus(MigrationArtifact.Status.FAILED);
            a.setErrorMessage(e.getMessage());
            a.setCompletedAt(Instant.now());
            artifacts.saveAndFlush(a);
            log.warn("Agent failed on {}: {}", a.getFilePath(), e.getMessage());
        }
    }

    // ---- multi-file split ---------------------------------------------

    private static final Pattern TARGET_HEADER =
            Pattern.compile("^\\s*//\\s*TARGET:\\s*(.+?)\\s*$", Pattern.MULTILINE);

    /** Splits an LLM response into (targetPath, code) pairs, one per TARGET header. */
    private static List<Split> splitByTargetHeaders(String content) {
        List<Split> out = new ArrayList<>();
        Matcher m = TARGET_HEADER.matcher(content);
        List<int[]> ranges = new ArrayList<>();
        List<String> paths = new ArrayList<>();
        while (m.find()) {
            ranges.add(new int[] { m.start(), m.end() });
            paths.add(m.group(1).trim());
        }
        if (ranges.isEmpty()) return out;
        for (int i = 0; i < ranges.size(); i++) {
            int codeStart = ranges.get(i)[1];
            int codeEnd = (i + 1 < ranges.size()) ? ranges.get(i + 1)[0] : content.length();
            String code = content.substring(codeStart, codeEnd).replaceFirst("^\\s*\\n", "").trim();
            out.add(new Split(paths.get(i), code));
        }
        return out;
    }

    private record Split(String targetPath, String code) {}

    // ---- lookups / helpers --------------------------------------------

    private MigrationArtifact.ValidationStatus toEntityStatus(ValidationService.Result.Status s) {
        return switch (s) {
            case VALID -> MigrationArtifact.ValidationStatus.VALID;
            case INVALID -> MigrationArtifact.ValidationStatus.INVALID;
            case SKIPPED -> MigrationArtifact.ValidationStatus.SKIPPED;
        };
    }

    public List<MigrationArtifact> list(Repo repo) {
        return artifacts.findByRepoIdOrderByPhaseNumberAscFilePathAsc(repo.getId());
    }

    public Optional<MigrationArtifact> detail(UUID artifactId) {
        return artifacts.findById(artifactId);
    }

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

    private String buildRetryPrompt(String originalPrompt, String previousCode, List<String> errors) {
        StringBuilder p = new StringBuilder(originalPrompt);
        p.append("\n\n--- PREVIOUS ATTEMPT ---\n").append(previousCode).append("\n--- END PREVIOUS ---\n\n");
        p.append("The previous attempt failed validation with these errors:\n");
        for (String err : errors) p.append("- ").append(err).append('\n');
        p.append("\nProduce a CORRECTED version. Same output format rules.");
        return p.toString();
    }

    private static final String SYSTEM_PROMPT = """
            You are a senior Java migration engineer. Given a single legacy source
            file and a target modernization approach, produce the complete
            modernized equivalent for Spring Boot 3 (Java 21) + Angular 18.

            Output rules — follow exactly:
            1. Return ONLY the source code. No prose, no explanation, no markdown fences.
            2. The FIRST line of EACH generated file MUST be a comment naming its path:
                 // TARGET: <full path>
            3. You MAY emit multiple files in one response — for example a Repository,
               its Entity, and a Service — by starting each with its own `// TARGET:` header.
               Each file must be a complete, syntactically valid compilation unit.
            4. Preserve business logic, field names, and behavior. Change framework mechanics only.
            5. Use Java 21 features (records, pattern matching, var) where they fit.
            6. Java classes: @RestController with @GetMapping/@PostMapping; @Service with
               constructor injection; JPA @Entity with @Id and @GeneratedValue.
            7. JSPs: convert to a single Angular 18 standalone component (.ts) with inline template.
            8. iBATIS/MyBatis mappers: convert to Spring Data JPA @Repository interfaces where possible.
            9. Config XML: convert to @Configuration classes or application.yml.
            10. Never emit TODOs or placeholders. Write real code.
            11. Every Java file must parse cleanly — balanced braces, terminated strings, real keywords.
            """;
}
