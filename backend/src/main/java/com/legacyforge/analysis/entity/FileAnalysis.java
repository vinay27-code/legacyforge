package com.legacyforge.analysis.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "file_analyses")
public class FileAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "analysis_id", nullable = false)
    private UUID analysisId;

    @Column(name = "file_id", nullable = false)
    private UUID fileId;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "class_name")
    private String className;

    @Column(name = "package_name")
    private String packageName;

    @Column(nullable = false)
    private int loc;

    @Column(name = "method_count", nullable = false)
    private int methodCount;

    @Column(nullable = false)
    private int complexity;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> imports = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> frameworks = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> findings = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() { createdAt = Instant.now(); }

    public FileAnalysis() {}

    public UUID getId() { return id; }
    public UUID getAnalysisId() { return analysisId; }
    public void setAnalysisId(UUID analysisId) { this.analysisId = analysisId; }
    public UUID getFileId() { return fileId; }
    public void setFileId(UUID fileId) { this.fileId = fileId; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }
    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }
    public int getLoc() { return loc; }
    public void setLoc(int loc) { this.loc = loc; }
    public int getMethodCount() { return methodCount; }
    public void setMethodCount(int methodCount) { this.methodCount = methodCount; }
    public int getComplexity() { return complexity; }
    public void setComplexity(int complexity) { this.complexity = complexity; }
    public List<String> getImports() { return imports; }
    public void setImports(List<String> imports) { this.imports = imports; }
    public List<String> getFrameworks() { return frameworks; }
    public void setFrameworks(List<String> frameworks) { this.frameworks = frameworks; }
    public List<Map<String, Object>> getFindings() { return findings; }
    public void setFindings(List<Map<String, Object>> findings) { this.findings = findings; }
}
