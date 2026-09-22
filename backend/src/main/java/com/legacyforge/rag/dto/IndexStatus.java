package com.legacyforge.rag.dto;

import java.util.UUID;

public record IndexStatus(
        UUID repoId,
        long chunkCount,
        String provider
) {}
