package com.legacyforge.planning.controller;

import com.legacyforge.ingestion.entity.Repo;
import com.legacyforge.ingestion.repo.RepoRepository;
import com.legacyforge.planning.dto.PlanDtos;
import com.legacyforge.planning.entity.MigrationPlan;
import com.legacyforge.planning.service.PlanningService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/repos/{repoId}/plan")
public class PlanningController {

    private final PlanningService planning;
    private final RepoRepository repos;

    public PlanningController(PlanningService planning, RepoRepository repos) {
        this.planning = planning;
        this.repos = repos;
    }

    /** Fetch existing plan; 404 if none yet. */
    @GetMapping
    public ResponseEntity<PlanDtos.PlanResponse> get(@PathVariable UUID repoId) {
        Repo repo = repos.findById(repoId).orElse(null);
        if (repo == null) return ResponseEntity.notFound().build();

        Optional<MigrationPlan> mp = planning.find(repo);
        return mp.map(m -> ResponseEntity.ok(planning.toResponse(m)))
                .orElse(ResponseEntity.notFound().build());
    }

    /** Generate a new plan (replaces any existing one for the repo). */
    @PostMapping
    public ResponseEntity<PlanDtos.PlanResponse> generate(@PathVariable UUID repoId) {
        Repo repo = repos.findById(repoId).orElse(null);
        if (repo == null) return ResponseEntity.notFound().build();

        MigrationPlan mp = planning.generate(repo);
        return ResponseEntity.ok(planning.toResponse(mp));
    }
}
