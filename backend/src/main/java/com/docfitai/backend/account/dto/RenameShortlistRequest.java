package com.docfitai.backend.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameShortlistRequest(
        @NotBlank(message = "Name is required") @Size(max = 100, message = "Name is too long") String name) {
}
