package com.legacyforge.stats.dto;

import java.time.Instant;
import java.util.List;

/**
 * Platform-wide aggregate stats surfaced on the Dashboard page.
 */
public class PlatformStats {

    public record Counts(
            long repos,
            long files,
            long chunks,
            long plans,
            long artifacts,
            long dependencyEdges,
            long brokenEdges,
            long planPatches
    ) {}

    public record ArtifactBreakdown(
            long success,
            long failed,
            long valid,
            long invalid,
            long skipped,
            long derived,
            long retriesTotal
    ) {}

    public record TokenUsage(
            long embeddingTokens,           // rough — chunk char count / 4 as heuristic
            long planningPromptTokens,
            long planningOutputTokens,
            long agentPromptTokens,
            long agentOutputTokens,
            long totalTokens,
            double estimatedCostUsd
    ) {}

    public record RecentActivity(
            String kind,                    // PLAN / AGENT_RUN / INDEX
            String repoName,
            Instant when,
            String summary
    ) {}

    public record Response(
            Counts counts,
            ArtifactBreakdown artifacts,
            TokenUsage tokens,
            List<RecentActivity> recent
    ) {}
}
