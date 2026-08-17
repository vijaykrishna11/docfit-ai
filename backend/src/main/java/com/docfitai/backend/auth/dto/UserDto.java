package com.docfitai.backend.auth.dto;

import java.time.Instant;

public record UserDto(Long id, String email, String displayName, Instant createdAt) {
}
