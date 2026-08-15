package com.docfitai.backend.provider.nppes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NppesResponse(@JsonProperty("result_count") int resultCount, List<NppesResult> results) {
}
