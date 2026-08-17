package com.docfitai.backend.provider;

/**
 * Search input boundary for {@link ProviderSearchService#search(ProviderSearchQuery)}.
 * Location is resolved with this precedence: lat/lng, then zip, then free-text location.
 *
 * <p>Practical-fit filters (all optional; null/false means "no filter applied", never a default
 * that silently narrows results): {@code providerType} ("INDIVIDUAL"/"ORGANIZATION"),
 * {@code hasPhone} (location has a phone number on file), {@code preciseLocationOnly} (coordinate
 * precision is ADDRESS_GEOCODE/EXACT, not a ZIP/city centroid approximation),
 * {@code networkEvidenceFound} (requires {@code planId}; EVIDENCE_FOUND status at the shown
 * location), {@code multipleLocations} (provider has more than one practice location).
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
        Long planId,
        String providerType,
        Boolean hasPhone,
        Boolean preciseLocationOnly,
        Boolean networkEvidenceFound,
        Boolean multipleLocations) {

    /** Convenience constructor for call sites with no practical-fit filters. */
    public ProviderSearchQuery(
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
        this(specialtyCode, zip, location, lat, lng, radiusMiles, sort, page, size, planId, null, null, null, null, null);
    }
}
