package com.docfitai.backend.account.dto;

import jakarta.validation.constraints.Size;

public record RenameSavedSearchRequest(@Size(max = 100, message = "Name is too long") String name) {
}
