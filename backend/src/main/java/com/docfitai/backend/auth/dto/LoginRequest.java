package com.docfitai.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank @Email(message = "Enter a valid email address") String email, @NotBlank String password) {
}
