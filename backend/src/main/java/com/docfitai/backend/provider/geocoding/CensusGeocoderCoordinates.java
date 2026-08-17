package com.docfitai.backend.provider.geocoding;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The Census Geocoder's raw {@code x}/{@code y} pair -- x is longitude, y is latitude (verified empirically). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CensusGeocoderCoordinates(double x, double y) {
}
