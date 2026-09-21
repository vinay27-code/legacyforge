package com.legacyforge.ingestion.repo;

import java.util.UUID;

/**
 * Lightweight projection: file tree nodes without their body content.
 */
public record RepoFileMetadata(
        UUID id,
        String path,
        long sizeBytes,
        String language,
        boolean binary
) {}
