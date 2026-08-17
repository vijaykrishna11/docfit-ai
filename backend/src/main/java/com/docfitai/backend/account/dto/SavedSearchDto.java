package com.docfitai.backend.account.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record SavedSearchDto(
        Long id,
        String name,
        String specialtyCode,
        String specialtyName,
        String locationText,
        BigDecimal latitude,
        BigDecimal longitude,
        int radius,
        String sort,
        Instant createdAt) {
}
