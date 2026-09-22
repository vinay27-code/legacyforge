package com.legacyforge.agents.controller;

import com.legacyforge.agents.dto.AgentDtos;
import com.legacyforge.agents.entity.MigrationArtifact;
import com.legacyforge.agents.entity.MigrationDependency;
import com.legacyforge.agents.repo.MigrationDependencyRepository;
import com.legacyforge.agents.service.MigrationAgentService;
import com.legacyforge.ingestion.entity.Repo;
import com.legacyforge.ingestion.repo.RepoRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/repos/{repoId}/agents")
public class MigrationAgentController {

    private final MigrationAgentService agents;
    private final MigrationDependencyRepository deps;
    private final RepoRepository repos;

    public MigrationAgentController(MigrationAgentService agents,
                                    MigrationDependencyRepository deps,
                                    RepoRepository repos) {
        this.agents = agents;
        this.deps = deps;
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

        // Build outgoing + incoming dependency lists for this artifact
        List<MigrationDependency> allEdges = deps.findByRepoId(repoId);
        Map<UUID, MigrationArtifact> idToArtifact = agents.list(repos.findById(repoId).orElseThrow()).stream()
                .collect(Collectors.toMap(MigrationArtifact::getId, x -> x));

        List<AgentDtos.DependencyEdge> outgoing = allEdges.stream()
                .filter(e -> e.getFromArtifactId().equals(a.getId()))
                .map(e -> toDepEdge(e, idToArtifact))
                .toList();

        List<AgentDtos.DependencyEdge> incoming = allEdges.stream()
                .filter(e -> a.getId().equals(e.getToArtifactId()))
                .map(e -> toDepEdge(e, idToArtifact))
                .toList();

        return ResponseEntity.ok(new AgentDtos.ArtifactDetail(
                a.getId(), a.getParentArtifactId(), a.getFilePath(), a.getTargetPath(),
                a.getDeclaredFqn(), a.getPhaseNumber(),
                a.getPhaseTitle(), a.getRisk(), a.getStatus().name(),
                a.getValidationStatus().name(), a.getValidationErrors(), a.getRetryCount(),
                a.getOriginalCode(), a.getGeneratedCode(), a.getErrorMessage(),
                a.getPromptTokens(), a.getOutputTokens(),
                a.getStartedAt(), a.getCompletedAt(),
                outgoing, incoming
        ));
    }

    private AgentDtos.DependencyEdge toDepEdge(MigrationDependency e,
                                               Map<UUID, MigrationArtifact> idToArtifact) {
        String targetPath = null;
        if (e.getToArtifactId() != null) {
            MigrationArtifact t = idToArtifact.get(e.getToArtifactId());
            if (t != null) targetPath = t.getTargetPath();
        }
        return new AgentDtos.DependencyEdge(
                e.getToClassName(), e.getToArtifactId(), targetPath,
                e.getEdgeType().name(), e.isResolved()
        );
    }

    private AgentDtos.RunSummary summary(UUID repoId, List<MigrationArtifact> all) {
        int total = all.size(), success = 0, failed = 0, pending = 0, running = 0;
        int valid = 0, invalid = 0, skipped = 0, totalRetries = 0, derived = 0;
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
            if (a.getParentArtifactId() != null) derived++;
        }

        // Dep aggregates: fetch once, group by artifact for out/broken/in counts
        List<MigrationDependency> allEdges = deps.findByRepoId(repoId);
        int totalEdges = allEdges.size();
        int brokenEdges = (int) allEdges.stream().filter(e -> !e.isResolved()).count();

        Map<UUID, int[]> perArtifact = new HashMap<>(); // [out, broken, in]
        for (MigrationArtifact a : all) perArtifact.put(a.getId(), new int[]{0, 0, 0});
        for (MigrationDependency e : allEdges) {
            int[] fromRow = perArtifact.get(e.getFromArtifactId());
            if (fromRow != null) {
                fromRow[0]++;
                if (!e.isResolved()) fromRow[1]++;
            }
            if (e.getToArtifactId() != null) {
                int[] toRow = perArtifact.get(e.getToArtifactId());
                if (toRow != null) toRow[2]++;
            }
        }

        List<AgentDtos.ArtifactSummary> summaries = all.stream().map(a -> {
            int[] counts = perArtifact.getOrDefault(a.getId(), new int[]{0, 0, 0});
            return new AgentDtos.ArtifactSummary(
                    a.getId(), a.getParentArtifactId(), a.getFilePath(), a.getTargetPath(),
                    a.getDeclaredFqn(), a.getPhaseNumber(), a.getPhaseTitle(), a.getRisk(),
                    a.getStatus().name(), a.getValidationStatus().name(), a.getRetryCount(),
                    counts[0], counts[1], counts[2],
                    a.getErrorMessage(), a.getPromptTokens(), a.getOutputTokens(),
                    a.getStartedAt(), a.getCompletedAt()
            );
        }).toList();

        return new AgentDtos.RunSummary(repoId, total, success, failed, pending, running,
                valid, invalid, skipped, totalRetries, derived, totalEdges, brokenEdges, summaries);
    }
}
