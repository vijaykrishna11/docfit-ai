package com.docfitai.backend.provider.dto;

import com.docfitai.backend.insurance.dto.NetworkEvidenceSummaryDto;

public record ProviderSearchResultDto(
        Long id,
        String npiNumber,
        String firstName,
        String lastName,
        String organizationName,
        String phone,
        String addressLine1,
        String addressLine2,
        String city,
        String stateCode,
        String postalCode,
        String taxonomyCode,
        String specialtyDisplayName,
        double distanceMiles,
        NetworkEvidenceSummaryDto networkEvidence) {

    /** Convenience constructor for call sites that don't have a plan selected -- networkEvidence is omitted (null), not a fabricated status. */
    public ProviderSearchResultDto(
            Long id,
            String npiNumber,
            String firstName,
            String lastName,
            String organizationName,
            String phone,
            String addressLine1,
            String addressLine2,
            String city,
            String stateCode,
            String postalCode,
            String taxonomyCode,
            String specialtyDisplayName,
            double distanceMiles) {
        this(
                id, npiNumber, firstName, lastName, organizationName, phone, addressLine1, addressLine2, city, stateCode,
                postalCode, taxonomyCode, specialtyDisplayName, distanceMiles, null);
    }

    public ProviderSearchResultDto withNetworkEvidence(NetworkEvidenceSummaryDto evidence) {
        return new ProviderSearchResultDto(
                id, npiNumber, firstName, lastName, organizationName, phone, addressLine1, addressLine2, city, stateCode,
                postalCode, taxonomyCode, specialtyDisplayName, distanceMiles, evidence);
    }
}
