package com.docfitai.backend.insurance.dto;

import java.time.Instant;

/** Compact evidence summary attached to a provider search result. Never a plain boolean. */
public record NetworkEvidenceSummaryDto(
        String status, String freshness, String planName, String networkName, boolean synthetic, Instant checkedAt) {
}
