package com.docfitai.backend.provider.dto;

public record ProviderNameSearchResultDto(
        Long id,
        String npiNumber,
        String firstName,
        String lastName,
        String organizationName,
        String city,
        String stateCode,
        String specialtyDisplayName) {
}
