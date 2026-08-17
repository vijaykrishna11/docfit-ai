package com.docfitai.backend.auth.dto;

import jakarta.validation.constraints.Size;

public record UpdateDisplayNameRequest(@Size(max = 100, message = "Name is too long") String displayName) {
}
