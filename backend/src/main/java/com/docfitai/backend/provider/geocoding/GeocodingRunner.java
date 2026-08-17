package com.docfitai.backend.provider.geocoding;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Operator CLI entry point for a bounded address geocoding batch (CLAUDE.md "Geocoding Pipeline"):
 * {@code DOCFIT_GEOCODE_ENABLED=true DOCFIT_GEOCODE_MAX_RECORDS=500
 * java -jar backend/target/backend-0.0.1-SNAPSHOT.jar}. Off by default; runs once at startup, then
 * the app continues running normally (same convention as the geography importer, not a one-shot
 * profile like {@code import}/{@code refresh}, since this can safely coexist with normal serving).
 */
@Component
@ConditionalOnProperty(prefix = "docfitai.geocode", name = "enabled", havingValue = "true")
public class GeocodingRunner implements CommandLineRunner {

    private final ProviderGeocodingService geocodingService;
    private final GeocodingProperties properties;

    public GeocodingRunner(ProviderGeocodingService geocodingService, GeocodingProperties properties) {
        this.geocodingService = geocodingService;
        this.properties = properties;
    }

    @Override
    public void run(String... args) {
        geocodingService.geocodeBoundedBatch(properties.getMaxRecords());
    }
}
