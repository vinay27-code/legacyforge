package com.legacyforge.analysis.dto;

import com.legacyforge.analysis.entity.FileAnalysis;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record FileAnalysisDto(
        UUID id,
        UUID fileId,
        String filePath,
        String className,
        String packageName,
        int loc,
        int methodCount,
        int complexity,
        List<String> frameworks,
        List<Map<String, Object>> findings
) {
    public static FileAnalysisDto from(FileAnalysis f) {
        return new FileAnalysisDto(
                f.getId(),
                f.getFileId(),
                f.getFilePath(),
                f.getClassName(),
                f.getPackageName(),
                f.getLoc(),
                f.getMethodCount(),
                f.getComplexity(),
                f.getFrameworks(),
                f.getFindings()
        );
    }
}
