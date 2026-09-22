package com.legacyforge.analysis.dto;

import com.legacyforge.analysis.entity.AnalysisReport;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AnalysisReportDto(
        UUID id,
        UUID repoId,
        String status,
        List<Map<String, Object>> frameworks,
        Map<String, Object> summary,
        List<Map<String, Object>> findings,
        int totalJavaFiles,
        long totalJavaLoc,
        int maxComplexity,
        BigDecimal avgComplexity,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt
) {
    public static AnalysisReportDto from(AnalysisReport r) {
        return new AnalysisReportDto(
                r.getId(),
                r.getRepoId(),
                r.getStatus().name(),
                r.getFrameworks(),
                r.getSummary(),
                r.getFindings(),
                r.getTotalJavaFiles(),
                r.getTotalJavaLoc(),
                r.getMaxComplexity(),
                r.getAvgComplexity(),
                r.getErrorMessage(),
                r.getCreatedAt(),
                r.getUpdatedAt()
        );
    }
}
