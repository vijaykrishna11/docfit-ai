package com.docfitai.backend.reference.geoimport;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Operator-controlled geography reference import (CLAUDE.md "Geography Import"). Off by default --
 * no surprise imports. {@code sourcePath} accepts a Spring resource location (e.g.
 * {@code classpath:geography/la-county-zip-geography.csv} for the bundled, source-verified LA
 * County reference set, or a plain filesystem path for an operator-supplied file) -- never
 * accepted from a request.
 */
@Component
@ConfigurationProperties(prefix = "docfitai.import.geography")
public class GeographyImportProperties {

    private boolean enabled = false;
    private String sourcePath = "classpath:geography/la-county-zip-geography.csv";
    private String sourceName = "U.S. Census Bureau 2024 ZCTA Gazetteer + 2020 ZCTA-County/Place Relationship Files";
    private String sourceVersion = "2024";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public String getSourceVersion() {
        return sourceVersion;
    }

    public void setSourceVersion(String sourceVersion) {
        this.sourceVersion = sourceVersion;
    }
}
