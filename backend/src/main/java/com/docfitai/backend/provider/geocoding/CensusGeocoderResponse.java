package com.docfitai.backend.provider.geocoding;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CensusGeocoderResponse(CensusGeocoderResult result) {
}
