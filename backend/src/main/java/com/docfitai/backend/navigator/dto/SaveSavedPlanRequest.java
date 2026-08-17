package com.docfitai.backend.navigator.dto;

import jakarta.validation.constraints.NotNull;

public record SaveSavedPlanRequest(@NotNull Long insurancePlanId) {
}
