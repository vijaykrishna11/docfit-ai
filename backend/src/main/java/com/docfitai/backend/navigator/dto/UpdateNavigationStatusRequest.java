package com.docfitai.backend.navigator.dto;

import com.docfitai.backend.navigator.NavigationStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateNavigationStatusRequest(@NotNull NavigationStatus status) {
}
