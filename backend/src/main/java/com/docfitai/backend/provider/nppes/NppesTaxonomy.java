package com.docfitai.backend.provider.nppes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NppesTaxonomy(String code, boolean primary, String desc) {
}
