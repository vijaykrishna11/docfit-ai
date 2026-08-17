package com.docfitai.backend.reference.dto;

/**
 * One autocomplete suggestion (CLAUDE.md "Location Suggestions V3"). {@code type} tells the
 * frontend the precision honesty it should communicate if this suggestion is selected: {@code ZIP}
 * suggestions carry a specific {@code zipCode} (ZIP_CENTROID-level precision when later used to
 * search); {@code CITY} suggestions represent one deduplicated city (never one row per ZIP within
 * that city) and carry a null {@code zipCode} -- selecting one searches by city/state text, which
 * resolves to a city-wide centroid (CITY_CENTROID-level precision), never a single ZIP's point.
 */
public record LocationSuggestionDto(String zipCode, String city, String stateCode, String label, String type) {

    public static final String TYPE_ZIP = "ZIP";
    public static final String TYPE_CITY = "CITY";
}
