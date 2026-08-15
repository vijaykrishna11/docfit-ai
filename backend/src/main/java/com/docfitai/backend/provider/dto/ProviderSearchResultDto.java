package com.docfitai.backend.provider.dto;

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
        double distanceMiles) {
}
