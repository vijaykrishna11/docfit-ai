package com.docfitai.backend.navigator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CreateReminderRequest(
        @NotBlank @Size(max = 200) String title, @NotNull Instant dueAt, Long providerId, Long shortlistId) {
}
