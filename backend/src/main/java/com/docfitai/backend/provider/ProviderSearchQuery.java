package com.docfitai.backend.provider;

/**
 * Search input boundary for {@link ProviderSearchService#search(ProviderSearchQuery)}.
 * Location is resolved with this precedence: lat/lng, then zip, then free-text location.
 */
public record ProviderSearchQuery(
        String specialtyCode,
        String zip,
        String location,
        Double lat,
        Double lng,
        double radiusMiles,
        String sort,
        int page,
        int size,
        Long planId) {
}
