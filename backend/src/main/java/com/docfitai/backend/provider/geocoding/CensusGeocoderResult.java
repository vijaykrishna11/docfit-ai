package com.docfitai.backend.provider.geocoding;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CensusGeocoderResult(List<CensusGeocoderMatch> addressMatches) {
}
