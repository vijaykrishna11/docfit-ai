package com.docfitai.backend.insurance.evidence;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configurable freshness thresholds (CLAUDE.md 14) -- not hardcoded, since 30/60 days are
 * reasonable defaults, not a clinically or contractually meaningful cutoff.
 */
@Component
@ConfigurationProperties(prefix = "docfitai.insurance.freshness")
public class FreshnessProperties {

    private int freshDays = 30;
    private int agingDays = 60;

    public int getFreshDays() {
        return freshDays;
    }

    public void setFreshDays(int freshDays) {
        this.freshDays = freshDays;
    }

    public int getAgingDays() {
        return agingDays;
    }

    public void setAgingDays(int agingDays) {
        this.agingDays = agingDays;
    }
}
