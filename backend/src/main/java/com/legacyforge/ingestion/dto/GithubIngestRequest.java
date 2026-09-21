package com.legacyforge.ingestion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record GithubIngestRequest(
        @NotBlank
        @Size(max = 1024)
        @Pattern(
                regexp = "^https?://github\\.com/[a-zA-Z0-9._-]+/[a-zA-Z0-9._-]+(?:\\.git)?/?$",
                message = "Must be a GitHub URL like https://github.com/OWNER/REPO"
        )
        String url
) {}
