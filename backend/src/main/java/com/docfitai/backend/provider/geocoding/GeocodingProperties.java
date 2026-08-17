package com.docfitai.backend.provider.geocoding;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Operator-controlled geocoding batch (CLAUDE.md "Geocoding Pipeline"). Off by default; when
 * enabled, geocodes up to {@code maxRecords} {@code ZIP_CENTROID} provider_location rows in this
 * one run.
 */
@Component
@ConfigurationProperties(prefix = "docfitai.geocode")
public class GeocodingProperties {

    private boolean enabled = false;
    private int maxRecords = 500;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxRecords() {
        return maxRecords;
    }

    public void setMaxRecords(int maxRecords) {
        this.maxRecords = maxRecords;
    }
}
