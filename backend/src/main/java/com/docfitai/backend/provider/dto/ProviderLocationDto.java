package com.docfitai.backend.provider.dto;

import java.math.BigDecimal;

/** One practice location. {@code coordinatePrecision} is a truthful label -- never claim EXACT for a ZIP-centroid lookup. */
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
        String coordinatePrecision) {
}
