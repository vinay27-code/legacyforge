package com.legacyforge.agents.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class AgentDtos {

    public record ArtifactSummary(
            UUID id,
            UUID parentArtifactId,
            String filePath,
            String targetPath,
            String declaredFqn,
            Integer phaseNumber,
            String phaseTitle,
            String risk,
            String status,
            String validationStatus,
            Integer retryCount,
            Integer depsOut,          // dependencies this artifact declares
            Integer depsBroken,       // of depsOut, how many point to unresolved classes
            Integer depsIn,           // other artifacts that reference this one
            String errorMessage,
            Integer promptTokens,
            Integer outputTokens,
            Instant startedAt,
            Instant completedAt
    ) {}

    public record DependencyEdge(
            String toClassName,
            UUID toArtifactId,     // null when unresolved
            String toTargetPath,   // convenience for UI
            String edgeType,
            boolean resolved
    ) {}

    public record ArtifactDetail(
            UUID id,
            UUID parentArtifactId,
            String filePath,
            String targetPath,
            String declaredFqn,
            Integer phaseNumber,
            String phaseTitle,
            String risk,
            String status,
            String validationStatus,
            String validationErrors,
            Integer retryCount,
            String originalCode,
            String generatedCode,
            String errorMessage,
            Integer promptTokens,
            Integer outputTokens,
            Instant startedAt,
            Instant completedAt,
            List<DependencyEdge> outgoing,
            List<DependencyEdge> incoming
    ) {}

    public record RunSummary(
            UUID repoId,
            int total,
            int success,
            int failed,
            int pending,
            int running,
            int valid,
            int invalid,
            int skipped,
            int totalRetries,
            int derivedCount,        // number of sibling artifacts spawned from multi-target LLM output
            int totalEdges,          // total dependency edges across the repo
            int brokenEdges,         // edges that don't resolve to any generated class
            List<ArtifactSummary> artifacts
    ) {}
}
