package com.docfitai.backend.provider.dto;

public record ProviderTaxonomyDto(
        String taxonomyCode, String classification, String specialization, String displayName, boolean primaryTaxonomy) {
}
