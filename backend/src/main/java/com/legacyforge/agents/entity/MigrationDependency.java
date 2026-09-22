package com.legacyforge.agents.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "migration_dependencies")
public class MigrationDependency {

    public enum EdgeType { IMPORT, FIELD, METHOD_PARAM, INSTANTIATION }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "repo_id", nullable = false)
    private UUID repoId;

    @Column(name = "from_artifact_id", nullable = false)
    private UUID fromArtifactId;

    @Column(name = "to_artifact_id")
    private UUID toArtifactId;

    @Column(name = "to_class_name", nullable = false, length = 512)
    private String toClassName;

    @Enumerated(EnumType.STRING)
    @Column(name = "edge_type", nullable = false, length = 32)
    private EdgeType edgeType;

    @Column(nullable = false)
    private boolean resolved = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getRepoId() { return repoId; }
    public void setRepoId(UUID repoId) { this.repoId = repoId; }
    public UUID getFromArtifactId() { return fromArtifactId; }
    public void setFromArtifactId(UUID fromArtifactId) { this.fromArtifactId = fromArtifactId; }
    public UUID getToArtifactId() { return toArtifactId; }
    public void setToArtifactId(UUID toArtifactId) { this.toArtifactId = toArtifactId; }
    public String getToClassName() { return toClassName; }
    public void setToClassName(String toClassName) { this.toClassName = toClassName; }
    public EdgeType getEdgeType() { return edgeType; }
    public void setEdgeType(EdgeType edgeType) { this.edgeType = edgeType; }
    public boolean isResolved() { return resolved; }
    public void setResolved(boolean resolved) { this.resolved = resolved; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
