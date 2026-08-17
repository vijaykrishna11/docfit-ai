package com.docfitai.backend.provider.nppes;

import com.docfitai.backend.provider.CoordinatePrecision;
import com.docfitai.backend.provider.ingestion.DataImport;
import com.docfitai.backend.provider.ingestion.DataImportRepository;
import com.docfitai.backend.provider.ingestion.ProviderDataQualityService;
import com.docfitai.backend.provider.ingestion.ProviderImportRecord;
import com.docfitai.backend.provider.ingestion.ProviderLocationRecord;
import com.docfitai.backend.provider.ingestion.ProviderUpsertService;
import com.docfitai.backend.reference.ZipGeography;
import com.docfitai.backend.reference.ZipGeographyRepository;
import java.math.BigDecimal;
import java.time.Instant;
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
 *
 * <p>Fetches both individual (NPI-1) and organization (NPI-2) providers, and imports every
 * practice location NPPES reports for each -- the single LOCATION address plus any genuine
 * additional offices in NPPES's {@code practiceLocations} field (CLAUDE.md 4, 19). A single bad
 * source record is logged and skipped rather than failing the whole import (CLAUDE.md 24).
 */
@Component
@Profile("import")
public class NppesImportRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(NppesImportRunner.class);
    private static final int RESULTS_PER_ZIP = 200;
    private static final List<String> ENUMERATION_TYPES = List.of("NPI-1", "NPI-2");

    private final NppesClient nppesClient;
    private final ZipGeographyRepository zipGeographyRepository;
    private final ProviderUpsertService providerUpsertService;
    private final DataImportRepository dataImportRepository;
    private final ProviderDataQualityService dataQualityService;
    private final JdbcTemplate jdbcTemplate;
    private final ConfigurableApplicationContext context;

    public NppesImportRunner(
            NppesClient nppesClient,
            ZipGeographyRepository zipGeographyRepository,
            ProviderUpsertService providerUpsertService,
            DataImportRepository dataImportRepository,
            ProviderDataQualityService dataQualityService,
            JdbcTemplate jdbcTemplate,
            ConfigurableApplicationContext context) {
        this.nppesClient = nppesClient;
        this.zipGeographyRepository = zipGeographyRepository;
        this.providerUpsertService = providerUpsertService;
        this.dataImportRepository = dataImportRepository;
        this.dataQualityService = dataQualityService;
        this.jdbcTemplate = jdbcTemplate;
        this.context = context;
    }

    @Override
    public void run(String... args) {
        DataImport dataImport = new DataImport("NPPES", Instant.now());
        dataImport = dataImportRepository.save(dataImport);

        Set<String> knownTaxonomyCodes =
                Set.copyOf(jdbcTemplate.queryForList("SELECT taxonomy_code FROM npi_taxonomy", String.class));
        List<ZipGeography> demoZips = zipGeographyRepository.findAll();

        int skippedNoMatch = 0;
        for (ZipGeography zip : demoZips) {
            for (String enumerationType : ENUMERATION_TYPES) {
                NppesResponse response = nppesClient.searchByPostalCode(zip.getZipCode(), enumerationType, RESULTS_PER_ZIP);
                log.info(
                        "NPPES postal_code={} enumeration_type={} returned {} raw results",
                        zip.getZipCode(),
                        enumerationType,
                        response.resultCount());

                List<NppesResult> results = response.results() == null ? List.of() : response.results();
                for (NppesResult result : results) {
                    Optional<NppesProviderMapper.MappedProvider> mapped = NppesProviderMapper.map(result, knownTaxonomyCodes);
                    if (mapped.isEmpty()) {
                        skippedNoMatch++;
                        continue;
                    }
                    try {
                        ProviderImportRecord importRecord = toImportRecord(mapped.get());
                        var outcome = providerUpsertService.upsert(importRecord, dataImport.getId());
                        dataImport.recordUpsert(outcome);
                    } catch (Exception e) {
                        log.warn("Failed to import NPI {}: {}", result.number(), e.getMessage());
                        dataImport.recordFailure();
                    }
                }
            }
        }

        dataImport.complete(Instant.now());
        dataImportRepository.save(dataImport);

        log.info(
                "NPPES import complete: status={} recordsRead={} providersCreated={} providersUpdated={} "
                        + "locationsCreated={} locationsUpdated={} recordsFailed={} skippedNoTaxonomyOrLocationMatch={}",
                dataImport.getStatus(),
                dataImport.getRecordsRead(),
                dataImport.getProvidersCreated(),
                dataImport.getProvidersUpdated(),
                dataImport.getLocationsCreated(),
                dataImport.getLocationsUpdated(),
                dataImport.getRecordsFailed(),
                skippedNoMatch);

        dataQualityService.runChecks();

        System.exit(SpringApplication.exit(context, () -> 0));
    }

    /** Geocoding (ZIP -> coordinates) happens here, not in the pure mapper -- always truthfully labeled ZIP_CENTROID, never a real address geocode. */
    private ProviderImportRecord toImportRecord(NppesProviderMapper.MappedProvider mapped) {
        List<ProviderLocationRecord> locationRecords = mapped.locations().stream()
                .map(location -> {
                    BigDecimal latitude = null;
                    BigDecimal longitude = null;
                    CoordinatePrecision precision = CoordinatePrecision.UNKNOWN;
                    Optional<ZipGeography> coordZip = zipGeographyRepository.findById(location.postalCode());
                    if (coordZip.isPresent()) {
                        latitude = coordZip.get().getLatitude();
                        longitude = coordZip.get().getLongitude();
                        precision = CoordinatePrecision.ZIP_CENTROID;
                    }
                    return new ProviderLocationRecord(
                            "LOCATION",
                            location.addressLine1(),
                            location.addressLine2(),
                            location.city(),
                            location.stateCode(),
                            location.postalCode(),
                            location.phone(),
                            location.fax(),
                            latitude,
                            longitude,
                            precision);
                })
                .toList();
        return new ProviderImportRecord(mapped.identity(), locationRecords, mapped.taxonomies());
    }
}
