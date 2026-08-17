package com.docfitai.backend.provider.dto;

import java.time.Instant;
import java.util.List;

/**
 * Real, runtime-queried aggregate counts only -- never hardcoded marketing numbers (CLAUDE.md
 * "Coverage Transparency UI" / "Coverage API V2").
 *
 * <p>Deliberately separates two different claims that must never be conflated (CLAUDE.md "Reference
 * Geography vs. Provider Data"): {@code geographyZipCount}/{@code geographyCityCount}/
 * {@code geographyCountyCount} describe the reference geography loaded into {@code zip_geography}
 * (which ZIPs DocFit *knows about*), while {@code providerZipCount}/{@code providerCityCount}/
 * {@code sampleProviderAreas} describe where provider data has *actually been imported*. Having
 * all of LA County's ZIPs in reference geography does not mean providers were imported for all of
 * them.
 */
public record CoverageDto(
        long providerCount,
        long locationCount,
        int specialtyCount,
        long geographyZipCount,
        long geographyCityCount,
        long geographyCountyCount,
        String geographySource,
        long providerZipCount,
        long providerCityCount,
        List<String> sampleProviderAreas,
        boolean sampleProviderAreasTruncated,
        Instant lastImportCompletedAt) {
}
