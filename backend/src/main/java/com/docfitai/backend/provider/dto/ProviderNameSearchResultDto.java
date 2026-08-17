package com.docfitai.backend.provider.dto;

public record ProviderNameSearchResultDto(
        Long id,
        String npiNumber,
        String entityType,
        String firstName,
        String lastName,
        String organizationName,
        String city,
        String stateCode,
        String specialtyDisplayName) {
}
