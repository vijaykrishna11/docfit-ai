package com.docfitai.backend.navigator.dto;

import java.time.Instant;

public record SavedPlanDto(
        Long id, Long payerId, String payerName, Long insurancePlanId, String planName, Instant createdAt, Instant updatedAt) {
}
