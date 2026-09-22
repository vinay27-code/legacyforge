package com.legacyforge.planning.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTOs the API sends to the frontend. The LLM's own JSON schema (what the model returns)
 * is a subset of what the frontend consumes; we add repo/provider/generated-at wrappers.
 */
public class PlanDtos {

    public record PlanResponse(
            UUID id,
            UUID repoId,
            String provider,
            Instant generatedAt,
            Integer promptTokens,
            Integer outputTokens,
            PlanContent plan
    ) {}

    public record PlanContent(
            String summary,
            String overallRisk,          // LOW | MEDIUM | HIGH
            Integer totalEstimatedDays,
            List<Phase> phases
    ) {}

    public record Phase(
            Integer phaseNumber,
            String title,
            String description,
            Integer estimatedDays,
            List<FileRisk> files
    ) {}

    public record FileRisk(
            String path,
            String risk,                 // LOW | MEDIUM | HIGH
            String reason,
            String notes
    ) {}
}
