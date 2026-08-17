package com.docfitai.backend.navigator.dto;

import com.docfitai.backend.navigator.VerificationItemStatus;
import com.docfitai.backend.navigator.VerificationType;
import java.time.Instant;

public record VerificationItemExportDto(
        Long providerId, VerificationType verificationType, VerificationItemStatus status, Instant confirmedAt, Instant updatedAt) {
}
