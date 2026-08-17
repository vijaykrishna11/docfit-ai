package com.docfitai.backend.account.dto;

import com.docfitai.backend.provider.dto.ProviderLocationDto;
import java.time.Instant;

public record SavedProviderDto(
        Long id,
        Instant savedAt,
        Long providerId,
        String npiNumber,
        String entityType,
        String firstName,
        String lastName,
        String organizationName,
        ProviderLocationDto location) {
}
