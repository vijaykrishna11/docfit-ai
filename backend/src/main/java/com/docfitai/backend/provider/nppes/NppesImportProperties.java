package com.docfitai.backend.provider.nppes;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Bounds a real NPPES import run to an explicit ZIP subset (CLAUDE.md "Import Cap" / "LA County
 * API Import Strategy") -- server-side configuration only, never accepted from a request. Empty
 * by default: the importer falls back to every row currently in {@code zip_geography} (unchanged
 * pre-existing behavior).
 */
@Component
@ConfigurationProperties(prefix = "docfitai.import.nppes")
public class NppesImportProperties {

    private List<String> zipCodes = List.of();

    public List<String> getZipCodes() {
        return zipCodes;
    }

    public void setZipCodes(List<String> zipCodes) {
        this.zipCodes = zipCodes;
    }
}
