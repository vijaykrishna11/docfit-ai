package com.docfitai.backend.provider.nppes;

import com.docfitai.backend.provider.Provider;
import com.docfitai.backend.provider.ProviderRepository;
import com.docfitai.backend.provider.ProviderTaxonomy;
import com.docfitai.backend.provider.ProviderTaxonomyId;
import com.docfitai.backend.provider.ProviderTaxonomyRepository;
import com.docfitai.backend.reference.ZipGeography;
import com.docfitai.backend.reference.ZipGeographyRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * One-time NPPES import for the TODAY-MVP demo dataset. Only runs when the "import" Spring
 * profile is explicitly activated (e.g. {@code ./mvnw spring-boot:run -Dspring-boot.run.profiles=import}).
 * Not a scheduled job, queue consumer, or background worker -- it exits the JVM once the
 * one-off import finishes.
 */
@Component
@Profile("import")
public class NppesImportRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(NppesImportRunner.class);
    private static final int RESULTS_PER_ZIP = 200;

    private final NppesClient nppesClient;
    private final ZipGeographyRepository zipGeographyRepository;
    private final ProviderRepository providerRepository;
    private final ProviderTaxonomyRepository providerTaxonomyRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ConfigurableApplicationContext context;

    public NppesImportRunner(
            NppesClient nppesClient,
            ZipGeographyRepository zipGeographyRepository,
            ProviderRepository providerRepository,
            ProviderTaxonomyRepository providerTaxonomyRepository,
            JdbcTemplate jdbcTemplate,
            ConfigurableApplicationContext context) {
        this.nppesClient = nppesClient;
        this.zipGeographyRepository = zipGeographyRepository;
        this.providerRepository = providerRepository;
        this.providerTaxonomyRepository = providerTaxonomyRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.context = context;
    }

    @Override
    public void run(String... args) {
        Set<String> knownTaxonomyCodes =
                Set.copyOf(jdbcTemplate.queryForList("SELECT taxonomy_code FROM npi_taxonomy", String.class));

        List<ZipGeography> demoZips = zipGeographyRepository.findAll();
        int imported = 0;
        int skippedExisting = 0;
        int skippedNoMatch = 0;

        for (ZipGeography zip : demoZips) {
            NppesResponse response = nppesClient.searchByPostalCode(zip.getZipCode(), RESULTS_PER_ZIP);
            log.info("NPPES postal_code={} returned {} raw results", zip.getZipCode(), response.resultCount());

            List<NppesResult> results = response.results() == null ? List.of() : response.results();
            for (NppesResult result : results) {
                Optional<NppesProviderMapper.MappedProvider> mapped =
                        NppesProviderMapper.map(result, knownTaxonomyCodes);
                if (mapped.isEmpty()) {
                    skippedNoMatch++;
                    continue;
                }

                NppesProviderMapper.MappedProvider mp = mapped.get();
                if (providerRepository.existsByNpiNumber(mp.npiNumber())) {
                    skippedExisting++;
                    continue;
                }

                BigDecimal latitude = null;
                BigDecimal longitude = null;
                Optional<ZipGeography> coordZip = zipGeographyRepository.findById(mp.postalCode());
                if (coordZip.isPresent()) {
                    latitude = coordZip.get().getLatitude();
                    longitude = coordZip.get().getLongitude();
                }

                Provider saved = providerRepository.save(new Provider(
                        mp.npiNumber(),
                        mp.firstName(),
                        mp.lastName(),
                        null,
                        mp.phone(),
                        mp.addressLine1(),
                        mp.addressLine2(),
                        mp.city(),
                        mp.stateCode(),
                        mp.postalCode(),
                        latitude,
                        longitude));

                for (NppesTaxonomy taxonomy : mp.matchingTaxonomies()) {
                    providerTaxonomyRepository.save(new ProviderTaxonomy(
                            new ProviderTaxonomyId(saved.getId(), taxonomy.code()), taxonomy.primary()));
                }
                imported++;
            }
        }

        log.info(
                "NPPES import complete: imported={} skippedExisting={} skippedNoMatch={}",
                imported,
                skippedExisting,
                skippedNoMatch);
        System.exit(SpringApplication.exit(context, () -> 0));
    }
}
