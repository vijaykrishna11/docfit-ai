package com.docfitai.backend.provider.dto;

import java.time.Instant;
import java.util.List;

/** Real, runtime-queried aggregate counts only -- never hardcoded marketing numbers (CLAUDE.md "Coverage Transparency UI"). */
public record CoverageDto(
        long providerCount, long locationCount, int specialtyCount, List<String> areas, Instant lastImportCompletedAt) {
}
