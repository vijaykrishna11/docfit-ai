package com.docfitai.backend.account.dto;

import java.time.Instant;

public record SavedProviderDto(
        Long id,
        Instant savedAt,
        Long providerId,
        String npiNumber,
        String firstName,
        String lastName,
        String organizationName,
        String phone,
        String addressLine1,
        String addressLine2,
        String city,
        String stateCode,
        String postalCode) {
}
