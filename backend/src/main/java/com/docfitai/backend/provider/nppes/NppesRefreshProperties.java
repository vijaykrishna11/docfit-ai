package com.docfitai.backend.provider.nppes;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Operator-controlled, bounded NPI list for a targeted refresh (CLAUDE.md "Operator-Triggerable
 * Provider Refresh"). Empty by default -- never refreshes anything unless an operator explicitly
 * supplies NPIs.
 */
@Component
@ConfigurationProperties(prefix = "docfitai.refresh.nppes")
public class NppesRefreshProperties {

    private List<String> npis = List.of();

    public List<String> getNpis() {
        return npis;
    }

    public void setNpis(List<String> npis) {
        this.npis = npis;
    }
}
