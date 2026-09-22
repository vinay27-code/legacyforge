package com.legacyforge.rag.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SearchRequest(
        @NotBlank @Size(max = 500) String query,
        @Min(1) @Max(50) Integer k
) {
    public int limit() { return k == null ? 10 : k; }
}
