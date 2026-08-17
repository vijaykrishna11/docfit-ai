package com.docfitai.backend.navigator.dto;

import java.time.Instant;

public record NavigatorShortlistSummaryDto(
        Long id, String name, long providerCount, long toContactCount, long contactedCount, Instant createdAt, Instant updatedAt) {
}
