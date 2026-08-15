package com.docfitai.backend.provider.dto;

import java.util.List;

public record ProviderSearchResponseDto(
        List<ProviderSearchResultDto> results,
        int page,
        int size,
        long totalElements,
        int totalPages,
        String originLabel) {
}
