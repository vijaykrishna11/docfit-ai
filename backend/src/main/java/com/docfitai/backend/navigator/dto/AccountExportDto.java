package com.docfitai.backend.navigator.dto;

import java.time.Instant;

/** Deliberately excludes passwordHash and any refresh-token data (CLAUDE.md "Data Export Security"). */
public record AccountExportDto(Long id, String email, String displayName, Instant createdAt) {
}
