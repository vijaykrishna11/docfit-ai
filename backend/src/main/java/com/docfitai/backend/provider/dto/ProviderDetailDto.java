package com.docfitai.backend.provider.dto;

import java.time.Instant;
import java.util.List;

/**
 * {@code location} is the selected/nearest practice location (nearest to the search origin when
 * one was given, else the primary location); {@code otherLocations} holds the provider's
 * remaining locations, if any (CLAUDE.md 13-14) -- never duplicated into {@code location}.
 */
public record ProviderDetailDto(
        Long id,
        String npiNumber,
        String entityType,
        String firstName,
        String lastName,
        String organizationName,
        ProviderLocationDto location,
        List<ProviderLocationDto> otherLocations,
        Double distanceMiles,
        List<ProviderTaxonomyDto> taxonomies,
        Instant importedAt) {
}
