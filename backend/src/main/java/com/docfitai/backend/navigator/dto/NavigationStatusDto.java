package com.docfitai.backend.navigator.dto;

import com.docfitai.backend.navigator.NavigationStatus;
import java.time.Instant;

public record NavigationStatusDto(Long providerId, NavigationStatus status, Instant updatedAt) {
}
