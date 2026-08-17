package com.docfitai.backend.navigator.dto;

import java.time.Instant;

/** {@code providerName}/{@code shortlistName} are denormalized read-time snapshots for display, not stored on the reminder. */
public record ReminderDto(
        Long id,
        String title,
        Instant dueAt,
        Instant completedAt,
        Long providerId,
        String providerName,
        Long shortlistId,
        String shortlistName,
        Instant createdAt) {
}
