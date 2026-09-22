package com.legacyforge.agents.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class AgentDtos {

    public record ArtifactSummary(
            UUID id,
            String filePath,
            String targetPath,
            Integer phaseNumber,
            String phaseTitle,
            String risk,
            String status,
            String errorMessage,
            Integer promptTokens,
            Integer outputTokens,
            Instant startedAt,
            Instant completedAt
    ) {}

    public record ArtifactDetail(
            UUID id,
            String filePath,
            String targetPath,
            Integer phaseNumber,
            String phaseTitle,
            String risk,
            String status,
            String originalCode,
            String generatedCode,
            String errorMessage,
            Integer promptTokens,
            Integer outputTokens,
            Instant startedAt,
            Instant completedAt
    ) {}

    public record RunSummary(
            UUID repoId,
            int total,
            int success,
            int failed,
            int pending,
            int running,
            List<ArtifactSummary> artifacts
    ) {}
}
