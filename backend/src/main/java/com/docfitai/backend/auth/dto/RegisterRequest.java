package com.docfitai.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email(message = "Enter a valid email address") String email,
        @NotBlank @Size(min = 8, max = 72, message = "Password must be at least 8 characters") String password,
        @Size(max = 100, message = "Name is too long") String displayName) {
}
