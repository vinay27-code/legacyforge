package com.legacyforge.ingestion.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "repo_files")
public class RepoFile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "repo_id", nullable = false)
    private UUID repoId;

    @Column(nullable = false)
    private String path;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column
    private String language;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "is_binary", nullable = false)
    private boolean binary;

    /**
     * Text content of the file for text files under 1MB.
     * Null for binary files and oversized files.
     */
    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public RepoFile() {}

    public RepoFile(UUID repoId, String path, long sizeBytes, String language,
                    String sha256, boolean binary, String content) {
        this.repoId = repoId;
        this.path = path;
        this.sizeBytes = sizeBytes;
        this.language = language;
        this.sha256 = sha256;
        this.binary = binary;
        this.content = content;
    }

    public UUID getId() { return id; }
    public UUID getRepoId() { return repoId; }
    public String getPath() { return path; }
    public long getSizeBytes() { return sizeBytes; }
    public String getLanguage() { return language; }
    public String getSha256() { return sha256; }
    public boolean isBinary() { return binary; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
}
