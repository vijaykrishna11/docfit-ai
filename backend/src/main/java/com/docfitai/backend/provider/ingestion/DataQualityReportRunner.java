package com.docfitai.backend.provider.ingestion;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Standalone operator CLI entry point for a data quality report on demand (CLAUDE.md "Operator CLI
 * Reference"), independent of running a real import: {@code ./mvnw spring-boot:run
 * -Dspring-boot.run.profiles=quality-report}. The same check already runs automatically after every
 * import ({@link ProviderDataQualityService#runChecks()}) -- this profile exists for checking
 * current data quality without triggering any import.
 */
@Component
@Profile("quality-report")
public class DataQualityReportRunner implements CommandLineRunner {

    private final ProviderDataQualityService dataQualityService;
    private final ConfigurableApplicationContext context;

    public DataQualityReportRunner(ProviderDataQualityService dataQualityService, ConfigurableApplicationContext context) {
        this.dataQualityService = dataQualityService;
        this.context = context;
    }

    @Override
    public void run(String... args) {
        dataQualityService.runChecks();
        System.exit(SpringApplication.exit(context, () -> 0));
    }
}
