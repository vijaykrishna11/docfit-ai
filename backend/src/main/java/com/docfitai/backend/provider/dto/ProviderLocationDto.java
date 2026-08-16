package com.docfitai.backend.provider.dto;

import java.math.BigDecimal;

/**
 * One practice location. {@code coordinatePrecision} is a truthful label -- never claim EXACT for
 * a ZIP-centroid lookup. {@code distanceMiles} is null whenever no search origin applies (e.g. the
 * saved-providers list, which has no search context, CLAUDE.md 17) -- never a fabricated distance.
 */
public record ProviderLocationDto(
        Long id,
        String addressLine1,
        String addressLine2,
        String city,
        String stateCode,
        String postalCode,
        String phone,
        BigDecimal latitude,
        BigDecimal longitude,
        String coordinatePrecision,
        Double distanceMiles) {

    /** Convenience constructor for call sites with no distance context (e.g. no search origin). */
    public ProviderLocationDto(
            Long id,
            String addressLine1,
            String addressLine2,
            String city,
            String stateCode,
            String postalCode,
            String phone,
            BigDecimal latitude,
            BigDecimal longitude,
            String coordinatePrecision) {
        this(id, addressLine1, addressLine2, city, stateCode, postalCode, phone, latitude, longitude, coordinatePrecision, null);
    }
}
