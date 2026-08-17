package com.docfitai.backend.account.dto;

import java.time.Instant;

/** Summary row for the shortlists index page -- provider count only, not the full provider list. */
public record ShortlistDto(Long id, String name, long providerCount, Instant createdAt, Instant updatedAt) {
}
