package com.legacyforge.analysis.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "analysis_reports")
public class AnalysisReport {

    public enum Status { PENDING, READY, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "repo_id", nullable = false)
    private UUID repoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> frameworks = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> summary = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> findings = new ArrayList<>();

    @Column(name = "total_java_files", nullable = false)
    private int totalJavaFiles;

    @Column(name = "total_java_loc", nullable = false)
    private long totalJavaLoc;

    @Column(name = "max_complexity", nullable = false)
    private int maxComplexity;

    @Column(name = "avg_complexity", nullable = false)
    private BigDecimal avgComplexity = BigDecimal.ZERO;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() { Instant now = Instant.now(); createdAt = now; updatedAt = now; }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public AnalysisReport() {}
    public AnalysisReport(UUID repoId) { this.repoId = repoId; }

    public UUID getId() { return id; }
    public UUID getRepoId() { return repoId; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public List<Map<String, Object>> getFrameworks() { return frameworks; }
    public void setFrameworks(List<Map<String, Object>> frameworks) { this.frameworks = frameworks; }
    public Map<String, Object> getSummary() { return summary; }
    public void setSummary(Map<String, Object> summary) { this.summary = summary; }
    public List<Map<String, Object>> getFindings() { return findings; }
    public void setFindings(List<Map<String, Object>> findings) { this.findings = findings; }
    public int getTotalJavaFiles() { return totalJavaFiles; }
    public void setTotalJavaFiles(int totalJavaFiles) { this.totalJavaFiles = totalJavaFiles; }
    public long getTotalJavaLoc() { return totalJavaLoc; }
    public void setTotalJavaLoc(long totalJavaLoc) { this.totalJavaLoc = totalJavaLoc; }
    public int getMaxComplexity() { return maxComplexity; }
    public void setMaxComplexity(int maxComplexity) { this.maxComplexity = maxComplexity; }
    public BigDecimal getAvgComplexity() { return avgComplexity; }
    public void setAvgComplexity(BigDecimal avgComplexity) { this.avgComplexity = avgComplexity; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
