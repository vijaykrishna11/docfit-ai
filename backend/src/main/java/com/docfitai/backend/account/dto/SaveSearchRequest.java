package com.docfitai.backend.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record SaveSearchRequest(
        @Size(max = 100, message = "Name is too long") String name,
        @NotBlank(message = "Specialty is required") String specialtyCode,
        @Size(max = 200) String locationText,
        Double latitude,
        Double longitude,
        @Positive(message = "Radius must be greater than zero") int radius,
        @NotBlank String sort) {
}
