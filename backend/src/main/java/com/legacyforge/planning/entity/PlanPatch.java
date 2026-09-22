package com.legacyforge.planning.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "plan_patches")
public class PlanPatch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "repo_id", nullable = false)
    private UUID repoId;

    @Column(name = "broken_class_names", columnDefinition = "TEXT", nullable = false)
    private String brokenClassNames;

    @Column(name = "files_added", nullable = false)
    private Integer filesAdded;

    @Column(name = "phases_added", nullable = false)
    private Integer phasesAdded;

    @Column(nullable = false)
    private String provider;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getPlanId() { return planId; }
    public void setPlanId(UUID planId) { this.planId = planId; }
    public UUID getRepoId() { return repoId; }
    public void setRepoId(UUID repoId) { this.repoId = repoId; }
    public String getBrokenClassNames() { return brokenClassNames; }
    public void setBrokenClassNames(String brokenClassNames) { this.brokenClassNames = brokenClassNames; }
    public Integer getFilesAdded() { return filesAdded; }
    public void setFilesAdded(Integer filesAdded) { this.filesAdded = filesAdded; }
    public Integer getPhasesAdded() { return phasesAdded; }
    public void setPhasesAdded(Integer phasesAdded) { this.phasesAdded = phasesAdded; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public Integer getPromptTokens() { return promptTokens; }
    public void setPromptTokens(Integer promptTokens) { this.promptTokens = promptTokens; }
    public Integer getOutputTokens() { return outputTokens; }
    public void setOutputTokens(Integer outputTokens) { this.outputTokens = outputTokens; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
