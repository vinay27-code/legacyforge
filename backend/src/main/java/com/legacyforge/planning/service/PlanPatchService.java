package com.legacyforge.planning.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.legacyforge.agents.entity.MigrationDependency;
import com.legacyforge.agents.repo.MigrationDependencyRepository;
import com.legacyforge.ingestion.entity.Repo;
import com.legacyforge.planning.entity.MigrationPlan;
import com.legacyforge.planning.repo.MigrationPlanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Feedback loop: takes the current plan + the list of broken class references
 * from the dependency graph and asks the LLM to propose a plan PATCH —
 * additional file entries to cover the missing classes, either slotted into
 * existing phases or grouped into a new gap-fill phase.
 *
 * The LLM's proposed additions are merged into the plan JSON in place, and
 * a plan_patches audit row records what happened.
 */
@Service
public class PlanPatchService {

    private static final Logger log = LoggerFactory.getLogger(PlanPatchService.class);

    private final LlmProvider llm;
    private final MigrationPlanRepository plans;
    private final MigrationDependencyRepository deps;
    private final ObjectMapper json;

    public PlanPatchService(LlmProvider llm,
                            MigrationPlanRepository plans,
                            MigrationDependencyRepository deps,
                            ObjectMapper json) {
        this.llm = llm;
        this.plans = plans;
        this.deps = deps;
        this.json = json;
    }

    @Transactional
    public PatchResult patchPlanFromBrokenLinks(Repo repo) {
        MigrationPlan plan = plans.findByRepoId(repo.getId())
                .orElseThrow(() -> new IllegalStateException("No plan yet — generate one first."));

        // Distinct broken classes only. Order by frequency so the most-referenced ones lead the prompt.
        Map<String, Long> brokenByFrequency = deps.findByRepoId(repo.getId()).stream()
                .filter(d -> !d.isResolved())
                .map(MigrationDependency::getToClassName)
                .filter(name -> name != null && !name.isBlank())
                .filter(name -> !name.contains("java.lang.")) // safety net; analyzer already skips these
                .collect(Collectors.groupingBy(n -> n, Collectors.counting()));

        if (brokenByFrequency.isEmpty()) {
            return new PatchResult(0, 0, 0, 0, "No broken references to patch.");
        }

        List<String> broken = brokenByFrequency.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .toList();

        JsonNode planNode;
        try {
            planNode = json.readTree(plan.getPlanJson());
        } catch (Exception e) {
            throw new RuntimeException("Stored plan JSON no longer parses: " + e.getMessage(), e);
        }

        String userPrompt = buildPatchPrompt(planNode, broken);
        log.info("Requesting plan patch for repo {}: {} distinct broken classes, prompt {} chars",
                repo.getId(), broken.size(), userPrompt.length());

        LlmProvider.LlmResponse resp = llm.completeJson(SYSTEM_PROMPT, userPrompt);
        String content = stripFences(resp.content());

        JsonNode patch;
        try {
            patch = json.readTree(content);
        } catch (Exception e) {
            log.error("Patch LLM returned invalid JSON: {}", content.substring(0, Math.min(400, content.length())));
            throw new RuntimeException("Patch LLM returned invalid JSON: " + e.getMessage(), e);
        }

        // Merge: any phaseNumber that already exists gets its files appended;
        // new phaseNumbers are added as new phase entries at the end.
        int filesAdded = 0;
        int phasesAdded = 0;

        ArrayNode existingPhases = (ArrayNode) planNode.path("phases");
        Map<Integer, ObjectNode> byNumber = new HashMap<>();
        int maxPhaseNumber = 0;
        for (JsonNode p : existingPhases) {
            int num = p.path("phaseNumber").asInt();
            byNumber.put(num, (ObjectNode) p);
            if (num > maxPhaseNumber) maxPhaseNumber = num;
        }

        for (JsonNode patchPhase : patch.path("phases")) {
            int patchNum = patchPhase.path("phaseNumber").asInt(0);
            ArrayNode patchFiles = (ArrayNode) patchPhase.path("files");
            if (patchFiles == null || patchFiles.isEmpty()) continue;

            if (patchNum > 0 && byNumber.containsKey(patchNum)) {
                // Slot the new files into an existing phase
                ArrayNode target = (ArrayNode) byNumber.get(patchNum).path("files");
                for (JsonNode f : patchFiles) {
                    target.add(f);
                    filesAdded++;
                }
            } else {
                // New phase — assign a fresh phaseNumber past the current max
                maxPhaseNumber++;
                ObjectNode newPhase = json.createObjectNode();
                newPhase.put("phaseNumber", maxPhaseNumber);
                newPhase.put("title", patchPhase.path("title").asText("Gap-fill phase " + maxPhaseNumber));
                newPhase.put("description", patchPhase.path("description").asText(
                        "Files added by the broken-links feedback loop"));
                newPhase.put("estimatedDays", patchPhase.path("estimatedDays").asInt(3));
                ArrayNode filesArr = json.createArrayNode();
                for (JsonNode f : patchFiles) {
                    filesArr.add(f);
                    filesAdded++;
                }
                newPhase.set("files", filesArr);
                existingPhases.add(newPhase);
                phasesAdded++;
            }
        }

        // Persist the updated plan
        plan.setPlanJson(json.writeValueAsString(planNode));
        plans.saveAndFlush(plan);

        return new PatchResult(broken.size(), filesAdded, phasesAdded,
                resp.promptTokens() != null ? resp.promptTokens() : 0,
                buildSummary(broken.size(), filesAdded, phasesAdded));
    }

    private String buildSummary(int brokenIn, int filesAdded, int phasesAdded) {
        return "Patched %d broken references → added %d files across %d %s"
                .formatted(brokenIn, filesAdded, phasesAdded,
                        phasesAdded == 1 ? "new phase" : "new phases");
    }

    private String buildPatchPrompt(JsonNode plan, List<String> broken) {
        StringBuilder p = new StringBuilder();
        p.append("You are patching an existing migration plan. Below is the current plan (as JSON) ")
                .append("and a list of Java class references that appeared in the generated code but ")
                .append("were NEVER placed in any phase — meaning the migration would have broken imports.\n\n");

        p.append("Current plan phases (phaseNumber → title):\n");
        for (JsonNode phase : plan.path("phases")) {
            p.append("- ").append(phase.path("phaseNumber").asInt())
                    .append(": ").append(phase.path("title").asText())
                    .append(" (").append(phase.path("files").size()).append(" files)\n");
        }

        p.append("\nBroken references (ordered by how many generated files use each):\n");
        for (String fqn : broken) {
            p.append("- ").append(fqn).append('\n');
        }

        p.append("\nFor each broken class, propose ONE file entry that would cover it. ")
                .append("Slot it into the most appropriate EXISTING phase by reusing that phase's phaseNumber, ")
                .append("or if none fit, group them into a new phase (any phaseNumber greater than the highest above).\n\n")
                .append("Guidelines:\n")
                .append("- Package like org.myapp.repository.X → Data Layer\n")
                .append("- Package like *.service.* → Service Layer\n")
                .append("- Package like *.controller.* or *.web.* → Controller Layer\n")
                .append("- Package like *.domain.* or *.model.* or *.entity.* → Data Layer (entities)\n")
                .append("- Config, DTO, exception, util classes → whichever phase they support\n\n")
                .append("File `path` in each entry should be the target source path derived from the FQN, e.g. ")
                .append("`src/main/java/org/myapp/repository/AccountRepository.java`. Set risk sensibly.");

        return p.toString();
    }

    private static final String SYSTEM_PROMPT = """
            You extend an existing Java migration plan with new file entries to
            close plan gaps discovered by dependency analysis.

            Return ONLY a valid JSON object matching this schema:

            {
              "phases": [
                {
                  "phaseNumber": <existing phase number to slot into, OR a new number greater than the max>,
                  "title": "Short phase title (only used when phaseNumber is new)",
                  "description": "Only used when phaseNumber is new",
                  "estimatedDays": <integer, only used when phaseNumber is new>,
                  "files": [
                    {
                      "path": "src/main/java/<derived from FQN>.java",
                      "risk": "LOW | MEDIUM | HIGH",
                      "reason": "Why this risk level",
                      "notes": "Concrete target class or pattern"
                    }
                  ]
                }
              ]
            }

            Rules:
            - Every broken class name in the input must appear as a file entry somewhere.
            - Prefer slotting into existing phases; only invent a new phaseNumber if
              nothing existing fits.
            - Keep phase titles/descriptions concise. `notes` must be actionable.
            - Do NOT restate the full plan; only return additions.
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

    public record PatchResult(
            int brokenReferencesConsidered,
            int filesAdded,
            int phasesAdded,
            int promptTokens,
            String summary
    ) {}
}
