package com.docfitai.backend.provider.nppes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NppesResult(
        String number,
        @JsonProperty("enumeration_type") String enumerationType,
        NppesBasic basic,
        List<NppesAddress> addresses,
        // Real, documented NPPES field for a provider's additional (non-primary) practice
        // locations, distinct from the single "LOCATION"-purpose entry in `addresses` -- this is
        // where genuine multi-location data comes from (see docs/provider-data-platform.md,
        // "NPPES importer changes").
        @JsonProperty("practiceLocations") List<NppesAddress> practiceLocations,
        List<NppesTaxonomy> taxonomies) {
}
