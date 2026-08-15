package com.docfitai.backend.provider.dto;

import java.util.List;

public record ProviderDetailDto(
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
        Double distanceMiles,
        List<ProviderTaxonomyDto> taxonomies) {
}
