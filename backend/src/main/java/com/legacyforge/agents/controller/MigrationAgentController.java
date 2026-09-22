package com.legacyforge.agents.controller;

import com.legacyforge.agents.dto.AgentDtos;
import com.legacyforge.agents.entity.MigrationArtifact;
import com.legacyforge.agents.service.MigrationAgentService;
import com.legacyforge.ingestion.entity.Repo;
import com.legacyforge.ingestion.repo.RepoRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/repos/{repoId}/agents")
public class MigrationAgentController {

    private final MigrationAgentService agents;
    private final RepoRepository repos;

    public MigrationAgentController(MigrationAgentService agents, RepoRepository repos) {
        this.agents = agents;
        this.repos = repos;
    }

    @GetMapping
    public ResponseEntity<AgentDtos.RunSummary> list(@PathVariable UUID repoId) {
        Repo repo = repos.findById(repoId).orElse(null);
        if (repo == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(summary(repoId, agents.list(repo)));
    }

    @PostMapping("/run")
    public ResponseEntity<AgentDtos.RunSummary> run(@PathVariable UUID repoId) {
        Repo repo = repos.findById(repoId).orElse(null);
        if (repo == null) return ResponseEntity.notFound().build();
        List<MigrationArtifact> result = agents.runAgainstPlan(repo);
        return ResponseEntity.ok(summary(repoId, result));
    }

    @GetMapping("/{artifactId}")
    public ResponseEntity<AgentDtos.ArtifactDetail> detail(@PathVariable UUID repoId,
                                                            @PathVariable UUID artifactId) {
        Optional<MigrationArtifact> mp = agents.detail(artifactId);
        if (mp.isEmpty() || !mp.get().getRepoId().equals(repoId)) {
            return ResponseEntity.notFound().build();
        }
        MigrationArtifact a = mp.get();
        return ResponseEntity.ok(new AgentDtos.ArtifactDetail(
                a.getId(), a.getFilePath(), a.getTargetPath(), a.getPhaseNumber(),
                a.getPhaseTitle(), a.getRisk(), a.getStatus().name(),
                a.getValidationStatus().name(), a.getValidationErrors(), a.getRetryCount(),
                a.getOriginalCode(), a.getGeneratedCode(), a.getErrorMessage(),
                a.getPromptTokens(), a.getOutputTokens(),
                a.getStartedAt(), a.getCompletedAt()
        ));
    }

    private AgentDtos.RunSummary summary(UUID repoId, List<MigrationArtifact> all) {
        int total = all.size(), success = 0, failed = 0, pending = 0, running = 0;
        int valid = 0, invalid = 0, skipped = 0, totalRetries = 0;
        for (MigrationArtifact a : all) {
            switch (a.getStatus()) {
                case SUCCESS -> success++;
                case FAILED -> failed++;
                case RUNNING -> running++;
                case PENDING -> pending++;
            }
            switch (a.getValidationStatus()) {
                case VALID -> valid++;
                case INVALID -> invalid++;
                case SKIPPED -> skipped++;
            }
            totalRetries += a.getRetryCount() == null ? 0 : a.getRetryCount();
        }
        List<AgentDtos.ArtifactSummary> summaries = all.stream().map(a -> new AgentDtos.ArtifactSummary(
                a.getId(), a.getFilePath(), a.getTargetPath(), a.getPhaseNumber(),
                a.getPhaseTitle(), a.getRisk(), a.getStatus().name(),
                a.getValidationStatus().name(), a.getRetryCount(),
                a.getErrorMessage(), a.getPromptTokens(), a.getOutputTokens(),
                a.getStartedAt(), a.getCompletedAt()
        )).toList();
        return new AgentDtos.RunSummary(repoId, total, success, failed, pending, running,
                valid, invalid, skipped, totalRetries, summaries);
    }
}
