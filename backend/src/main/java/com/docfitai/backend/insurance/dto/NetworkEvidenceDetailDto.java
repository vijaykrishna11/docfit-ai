package com.docfitai.backend.insurance.dto;

import java.time.Instant;
import java.util.List;

/** Full evidence detail for the "View network evidence" drawer. */
public record NetworkEvidenceDetailDto(
        Long providerId,
        Long planId,
        String planName,
        String networkName,
        String payerName,
        String status,
        String freshness,
        String matchedAddressLine1,
        String matchedCity,
        String matchedStateCode,
        String matchedPostalCode,
        String matchMethod,
        String sourceName,
        String sourceType,
        boolean synthetic,
        Instant checkedAt,
        Instant firstSeenAt,
        List<String> limitations) {
}
