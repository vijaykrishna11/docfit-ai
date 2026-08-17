package com.docfitai.backend.provider.nppes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Operator CLI entry point for a targeted refresh (CLAUDE.md "Operator-Triggerable Provider
 * Refresh"): {@code ./mvnw spring-boot:run -Dspring-boot.run.profiles=refresh
 * -DDOCFIT_REFRESH_NPIS=1234567890,1234567891}. Only runs on the explicit "refresh" profile, never
 * automatically -- there is deliberately no public HTTP endpoint that can trigger this.
 */
@Component
@Profile("refresh")
public class NppesRefreshRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(NppesRefreshRunner.class);

    private final ProviderRefreshService refreshService;
    private final NppesRefreshProperties refreshProperties;
    private final ConfigurableApplicationContext context;

    public NppesRefreshRunner(
            ProviderRefreshService refreshService, NppesRefreshProperties refreshProperties, ConfigurableApplicationContext context) {
        this.refreshService = refreshService;
        this.refreshProperties = refreshProperties;
        this.context = context;
    }

    @Override
    public void run(String... args) {
        if (refreshProperties.getNpis().isEmpty()) {
            log.warn("No NPIs configured (docfitai.refresh.nppes.npis / DOCFIT_REFRESH_NPIS) -- nothing to refresh.");
        } else {
            refreshService.refreshByNpis(refreshProperties.getNpis());
        }
        System.exit(SpringApplication.exit(context, () -> 0));
    }
}
