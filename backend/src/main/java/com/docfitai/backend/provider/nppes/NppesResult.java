package com.docfitai.backend.provider.nppes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NppesResult(String number, NppesBasic basic, List<NppesAddress> addresses, List<NppesTaxonomy> taxonomies) {
}
