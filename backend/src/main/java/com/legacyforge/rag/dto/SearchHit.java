package com.legacyforge.rag.dto;

import java.util.UUID;

public record SearchHit(
        UUID id,
        UUID fileId,
        String filePath,
        String chunkType,
        String className,
        String methodName,
        Integer startLine,
        Integer endLine,
        String content,
        double similarity
) {}
