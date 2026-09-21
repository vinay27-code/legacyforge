package com.legacyforge.ingestion.dto;

public record FileContent(
        String path,
        long sizeBytes,
        String language,
        boolean binary,
        String content
) {}
