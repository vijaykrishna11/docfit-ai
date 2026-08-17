package com.docfitai.backend.provider.ingestion;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Operator-controlled CSV import configuration (CLAUDE.md 28-31). {@code sourceDirectory} must
 * be set by the operator via server-side configuration -- never accepted from a request, so
 * there is no arbitrary-filesystem-read surface (CLAUDE.md 30).
 */
@Component
@ConfigurationProperties(prefix = "docfitai.import.csv")
public class ProviderCsvImportProperties {

    private boolean enabled = false;
    private String sourceDirectory;
    private int batchSize = 200;
    // Operator dry-run (CLAUDE.md "Operator Dry-Run"): parse/validate/count only, never write to
    // the database. Independent of `enabled` -- a dry run is meaningful even before an operator
    // is ready to actually import.
    private boolean dryRun = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public String getSourceDirectory() {
        return sourceDirectory;
    }

    public void setSourceDirectory(String sourceDirectory) {
        this.sourceDirectory = sourceDirectory;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }
}
