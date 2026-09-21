package com.legacyforge.ingestion.dto;

import com.legacyforge.ingestion.entity.Repo;

import java.time.Instant;
import java.util.UUID;

public record RepoSummary(
        UUID id,
        String name,
        String sourceType,
        String sourceUrl,
        int fileCount,
        long totalSizeBytes,
        String status,
        String errorMessage,
        Instant createdAt
) {
    public static RepoSummary from(Repo r) {
        return new RepoSummary(
                r.getId(),
                r.getName(),
                r.getSourceType().name(),
                r.getSourceUrl(),
                r.getFileCount(),
                r.getTotalSizeBytes(),
                r.getStatus().name(),
                r.getErrorMessage(),
                r.getCreatedAt()
        );
    }
}
