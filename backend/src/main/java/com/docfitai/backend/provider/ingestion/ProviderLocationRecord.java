package com.docfitai.backend.provider.ingestion;

import com.docfitai.backend.provider.CoordinatePrecision;
import java.math.BigDecimal;

/** Source-agnostic practice location, produced by any importer. */
public record ProviderLocationRecord(
        String addressPurpose,
        String addressLine1,
        String addressLine2,
        String city,
        String stateCode,
        String postalCode,
        String phone,
        String fax,
        BigDecimal latitude,
        BigDecimal longitude,
        CoordinatePrecision coordinatePrecision) {
}
