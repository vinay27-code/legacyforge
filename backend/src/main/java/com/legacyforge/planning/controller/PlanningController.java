package com.legacyforge.planning.controller;

import com.legacyforge.ingestion.entity.Repo;
import com.legacyforge.ingestion.repo.RepoRepository;
import com.legacyforge.planning.dto.PlanDtos;
import com.legacyforge.planning.entity.MigrationPlan;
import com.legacyforge.planning.service.PlanPatchService;
import com.legacyforge.planning.service.PlanningService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/repos/{repoId}/plan")
public class PlanningController {

    private final PlanningService planning;
    private final PlanPatchService patch;
    private final RepoRepository repos;

    public PlanningController(PlanningService planning, PlanPatchService patch, RepoRepository repos) {
        this.planning = planning;
        this.patch = patch;
        this.repos = repos;
    }

    @GetMapping
    public ResponseEntity<PlanDtos.PlanResponse> get(@PathVariable UUID repoId) {
        Repo repo = repos.findById(repoId).orElse(null);
        if (repo == null) return ResponseEntity.notFound().build();

        Optional<MigrationPlan> mp = planning.find(repo);
        return mp.map(m -> ResponseEntity.ok(planning.toResponse(m)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<PlanDtos.PlanResponse> generate(@PathVariable UUID repoId) {
        Repo repo = repos.findById(repoId).orElse(null);
        if (repo == null) return ResponseEntity.notFound().build();

        MigrationPlan mp = planning.generate(repo);
        return ResponseEntity.ok(planning.toResponse(mp));
    }

    /**
     * Feedback loop: takes the dependency graph's broken references and asks
     * the LLM to add plan entries covering the missing classes. Returns a
     * summary of what got added; the caller should then rerun the agents
     * against the patched plan to fill the new slots.
     */
    @PostMapping("/patch")
    public ResponseEntity<PatchResponse> patch(@PathVariable UUID repoId) {
        Repo repo = repos.findById(repoId).orElse(null);
        if (repo == null) return ResponseEntity.notFound().build();

        PlanPatchService.PatchResult r = patch.patchPlanFromBrokenLinks(repo);
        return ResponseEntity.ok(new PatchResponse(
                r.brokenReferencesConsidered(),
                r.filesAdded(),
                r.phasesAdded(),
                r.promptTokens(),
                r.summary()
        ));
    }

    public record PatchResponse(
            int brokenReferencesConsidered,
            int filesAdded,
            int phasesAdded,
            int promptTokens,
            String summary
    ) {}
}
