package com.docfitai.backend.navigator.dto;

import com.docfitai.backend.insurance.dto.NetworkEvidenceSummaryDto;
import com.docfitai.backend.navigator.NavigationStatus;
import com.docfitai.backend.provider.dto.ProviderLocationDto;
import java.time.Instant;

/**
 * One saved provider's full navigator card: identity, the user's own status/checklist progress
 * (never a DocFit-asserted fact), and a factual, rule-based {@code nextAction} label
 * (CLAUDE.md "Next Action" / "Progress -- No Gamification": a count, never a score).
 */
public record NavigatorProviderDto(
        Long providerId,
        String npiNumber,
        String entityType,
        String firstName,
        String lastName,
        String organizationName,
        ProviderLocationDto location,
        NavigationStatus status,
        int verificationCompleted,
        int verificationTotal,
        NetworkEvidenceSummaryDto networkEvidence,
        String nextAction,
        Instant savedAt) {
}
