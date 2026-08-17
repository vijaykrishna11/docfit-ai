package com.docfitai.backend.navigator.dto;

import com.docfitai.backend.navigator.VerificationItemStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateVerificationItemRequest(@NotNull VerificationItemStatus status, Long providerLocationId) {
}
