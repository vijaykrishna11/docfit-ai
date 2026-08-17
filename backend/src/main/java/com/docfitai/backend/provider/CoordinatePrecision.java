package com.docfitai.backend.provider;

/**
 * Truthful label for how a {@link ProviderLocation}'s coordinates were derived. Never claim
 * {@code EXACT} for a lookup that was actually a ZIP-centroid approximation.
 */
public enum CoordinatePrecision {
    /** A real, verified rooftop/parcel-level geocode. */
    EXACT,
    /** A geocoder resolved the street address, but the result wasn't independently verified. */
    ADDRESS_GEOCODE,
    /** Coordinates came from a ZIP code centroid lookup, not the actual street address. */
    ZIP_CENTROID,
    /** Coordinates came from a city-level centroid. */
    CITY_CENTROID,
    /** No coordinates available. */
    UNKNOWN
}
