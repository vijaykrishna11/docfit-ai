package com.docfitai.backend.provider.ingestion;

import com.docfitai.backend.provider.ProviderEntityType;

/** Source-agnostic provider identity, produced by any importer (NPPES, CSV, ...). */
public record ProviderIdentityRecord(
        String npiNumber, ProviderEntityType entityType, String firstName, String lastName, String organizationName) {
}
