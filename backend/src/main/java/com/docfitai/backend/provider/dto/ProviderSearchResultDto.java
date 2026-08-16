package com.docfitai.backend.provider.dto;

import com.docfitai.backend.insurance.dto.NetworkEvidenceSummaryDto;

/**
 * One provider per result (never repeated per office, CLAUDE.md 2), carrying the single nearest
 * qualifying {@link ProviderLocationDto} for this search's origin/radius.
 */
public record ProviderSearchResultDto(
        Long id,
        String npiNumber,
        String entityType,
        String firstName,
        String lastName,
        String organizationName,
        String taxonomyCode,
        String specialtyDisplayName,
        ProviderLocationDto location,
        double distanceMiles,
        NetworkEvidenceSummaryDto networkEvidence) {

    /** Convenience constructor for call sites that don't have a plan selected -- networkEvidence is omitted (null), not a fabricated status. */
    public ProviderSearchResultDto(
            Long id,
            String npiNumber,
            String entityType,
            String firstName,
            String lastName,
            String organizationName,
            String taxonomyCode,
            String specialtyDisplayName,
            ProviderLocationDto location,
            double distanceMiles) {
        this(id, npiNumber, entityType, firstName, lastName, organizationName, taxonomyCode, specialtyDisplayName, location, distanceMiles, null);
    }

    public ProviderSearchResultDto withNetworkEvidence(NetworkEvidenceSummaryDto evidence) {
        return new ProviderSearchResultDto(
                id, npiNumber, entityType, firstName, lastName, organizationName, taxonomyCode, specialtyDisplayName, location,
                distanceMiles, evidence);
    }
}
