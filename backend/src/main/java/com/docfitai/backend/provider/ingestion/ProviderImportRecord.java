package com.docfitai.backend.provider.ingestion;

import java.util.List;

/** One source record fully mapped to DocFit's domain: one provider, its locations, and its matched taxonomies. */
public record ProviderImportRecord(
        ProviderIdentityRecord identity, List<ProviderLocationRecord> locations, List<ProviderTaxonomyRecord> taxonomies) {
}
