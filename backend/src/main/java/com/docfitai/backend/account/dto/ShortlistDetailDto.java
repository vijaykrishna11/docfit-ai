package com.docfitai.backend.account.dto;

import java.time.Instant;
import java.util.List;

public record ShortlistDetailDto(Long id, String name, List<ShortlistProviderDto> providers, Instant createdAt, Instant updatedAt) {
}
