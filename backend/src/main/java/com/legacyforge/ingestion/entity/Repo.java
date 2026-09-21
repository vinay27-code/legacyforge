package com.legacyforge.ingestion.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "repos")
public class Repo {

    public enum SourceType { ZIP, GITHUB }
    public enum Status { PENDING, READY, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private SourceType sourceType;

    @Column(name = "source_url")
    private String sourceUrl;

    @Column(name = "file_count", nullable = false)
    private int fileCount = 0;

    @Column(name = "total_size_bytes", nullable = false)
    private long totalSizeBytes = 0L;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Repo() {}

    public Repo(UUID userId, String name, SourceType sourceType, String sourceUrl) {
        this.userId = userId;
        this.name = name;
        this.sourceType = sourceType;
        this.sourceUrl = sourceUrl;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getName() { return name; }
    public SourceType getSourceType() { return sourceType; }
    public String getSourceUrl() { return sourceUrl; }
    public int getFileCount() { return fileCount; }
    public void setFileCount(int fileCount) { this.fileCount = fileCount; }
    public long getTotalSizeBytes() { return totalSizeBytes; }
    public void setTotalSizeBytes(long totalSizeBytes) { this.totalSizeBytes = totalSizeBytes; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
