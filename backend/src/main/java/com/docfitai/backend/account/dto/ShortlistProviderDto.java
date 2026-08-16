package com.docfitai.backend.account.dto;

import com.docfitai.backend.provider.dto.ProviderLocationDto;
import java.time.Instant;

/** One provider entry within a shortlist detail view. No clinical notes field by design (CLAUDE.md "No Free-Text Medical Notes"). */
public record ShortlistProviderDto(
        Long providerId,
        Instant addedAt,
        String npiNumber,
        String entityType,
        String firstName,
        String lastName,
        String organizationName,
        ProviderLocationDto location) {
}
