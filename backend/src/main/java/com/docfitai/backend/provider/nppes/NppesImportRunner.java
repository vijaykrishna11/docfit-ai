package com.docfitai.backend.provider.nppes;

import com.docfitai.backend.provider.ingestion.DataImport;
import com.docfitai.backend.provider.ingestion.DataImportRepository;
import com.docfitai.backend.provider.ingestion.ProviderDataQualityService;
import com.docfitai.backend.provider.ingestion.ProviderImportRecord;
import com.docfitai.backend.provider.ingestion.ProviderUpsertService;
import com.docfitai.backend.reference.ZipGeography;
import com.docfitai.backend.reference.ZipGeographyRepository;
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
    private static final int RESULTS_PER_PAGE = 200;
    // Bounded pagination (CLAUDE.md "Import Cap" / "Investigate NPPES API Limitations"): up to 3
    // pages (600 results) per ZIP+entity-type combination -- the API's own per-request limit is
    // fixed at 200 regardless of what's requested, verified empirically, so a single call was
    // silently missing any ZIP+type with more than 200 matches. 3 pages is a deliberately modest
    // cap, not "paginate until exhausted" -- a genuinely denser ZIP simply isn't fully captured in
    // one bounded run, which is honest (PARTIAL scope), not a bug.
    private static final int MAX_PAGES_PER_QUERY = 3;
    // Absolute safety ceiling across the whole run, regardless of ZIP count (CLAUDE.md "Import
    // Cap": "Never accidental infinite pagination").
    private static final int MAX_TOTAL_REQUESTS = 1200;
    private static final List<String> ENUMERATION_TYPES = List.of("NPI-1", "NPI-2");

    private final NppesClient nppesClient;
    private final ZipGeographyRepository zipGeographyRepository;
    private final ProviderUpsertService providerUpsertService;
    private final DataImportRepository dataImportRepository;
    private final ProviderDataQualityService dataQualityService;
    private final JdbcTemplate jdbcTemplate;
    private final ConfigurableApplicationContext context;
    private final NppesImportProperties importProperties;
    private final NppesRecordFactory recordFactory;

    public NppesImportRunner(
            NppesClient nppesClient,
            ZipGeographyRepository zipGeographyRepository,
            ProviderUpsertService providerUpsertService,
            DataImportRepository dataImportRepository,
            ProviderDataQualityService dataQualityService,
            JdbcTemplate jdbcTemplate,
            ConfigurableApplicationContext context,
            NppesImportProperties importProperties,
            NppesRecordFactory recordFactory) {
        this.nppesClient = nppesClient;
        this.zipGeographyRepository = zipGeographyRepository;
        this.providerUpsertService = providerUpsertService;
        this.dataImportRepository = dataImportRepository;
        this.dataQualityService = dataQualityService;
        this.jdbcTemplate = jdbcTemplate;
        this.context = context;
        this.importProperties = importProperties;
        this.recordFactory = recordFactory;
    }

    @Override
    public void run(String... args) {
        DataImport dataImport = new DataImport("NPPES", Instant.now());
        dataImport = dataImportRepository.save(dataImport);

        Set<String> knownTaxonomyCodes =
                Set.copyOf(jdbcTemplate.queryForList("SELECT taxonomy_code FROM npi_taxonomy", String.class));
        List<ZipGeography> allZips = zipGeographyRepository.findAll();
        // Bounded scope (CLAUDE.md "Import Cap"): an explicit ZIP list restricts a real run to a
        // calculated subset instead of every row currently in zip_geography.
        List<String> scopedZipCodes = importProperties.getZipCodes();
        List<ZipGeography> demoZips = scopedZipCodes.isEmpty()
                ? allZips
                : allZips.stream().filter(z -> scopedZipCodes.contains(z.getZipCode())).toList();
        log.info(
                "NPPES import scope: {} of {} zip_geography rows ({})",
                demoZips.size(),
                allZips.size(),
                scopedZipCodes.isEmpty() ? "unbounded -- all rows" : "bounded via docfitai.import.nppes.zip-codes");

        int skippedNoMatch = 0;
        int totalRequests = 0;
        boolean capReached = false;
        outer:
        for (ZipGeography zip : demoZips) {
            for (String enumerationType : ENUMERATION_TYPES) {
                for (int page = 0; page < MAX_PAGES_PER_QUERY; page++) {
                    if (totalRequests >= MAX_TOTAL_REQUESTS) {
                        log.warn("NPPES import reached its MAX_TOTAL_REQUESTS safety ceiling ({}); stopping early.", MAX_TOTAL_REQUESTS);
                        capReached = true;
                        break outer;
                    }
                    int skip = page * RESULTS_PER_PAGE;
                    NppesResponse response =
                            nppesClient.searchByPostalCode(zip.getZipCode(), enumerationType, RESULTS_PER_PAGE, skip);
                    totalRequests++;
                    log.info(
                            "NPPES postal_code={} enumeration_type={} skip={} returned {} raw results",
                            zip.getZipCode(),
                            enumerationType,
                            skip,
                            response.resultCount());

                    List<NppesResult> results = response.results() == null ? List.of() : response.results();
                    for (NppesResult result : results) {
                        Optional<NppesProviderMapper.MappedProvider> mapped = NppesProviderMapper.map(result, knownTaxonomyCodes);
                        if (mapped.isEmpty()) {
                            skippedNoMatch++;
                            continue;
                        }
                        try {
                            ProviderImportRecord importRecord = recordFactory.toImportRecord(mapped.get());
                            var outcome = providerUpsertService.upsert(importRecord, dataImport.getId());
                            dataImport.recordUpsert(outcome);
                        } catch (Exception e) {
                            log.warn("Failed to import NPI {}: {}", result.number(), e.getMessage());
                            dataImport.recordFailure();
                        }
                    }
                    // A page smaller than the per-request cap means there's nothing more to page
                    // through for this ZIP+type -- stop early rather than making a guaranteed-empty
                    // extra request.
                    if (response.resultCount() < RESULTS_PER_PAGE) {
                        break;
                    }
                }
            }
        }

        // PARTIAL, always -- this run only ever covers a bounded ZIP subset and a bounded page
        // count per query, never a source-guaranteed-complete scope (CLAUDE.md "Partial Safety" /
        // "Import Scope Metadata": "Do NOT mark county import complete unless source partitioning
        // really guarantees completeness").
        dataImport.setScope(
                "PARTIAL",
                "NPPES API, %d ZIP(s), up to %d page(s) (%d records) per ZIP/entity-type combination%s"
                        .formatted(
                                demoZips.size(),
                                MAX_PAGES_PER_QUERY,
                                MAX_PAGES_PER_QUERY * RESULTS_PER_PAGE,
                                capReached ? " -- stopped early at the request safety ceiling" : ""));
        dataImport.complete(Instant.now());
        dataImportRepository.save(dataImport);

        log.info(
                "NPPES import complete: status={} scope={} recordsRead={} providersCreated={} providersUpdated={} "
                        + "locationsCreated={} locationsUpdated={} recordsFailed={} skippedNoTaxonomyOrLocationMatch={} totalRequests={}",
                dataImport.getStatus(),
                dataImport.getScopeType(),
                dataImport.getRecordsRead(),
                dataImport.getProvidersCreated(),
                dataImport.getProvidersUpdated(),
                dataImport.getLocationsCreated(),
                dataImport.getLocationsUpdated(),
                dataImport.getRecordsFailed(),
                skippedNoMatch,
                totalRequests);

        dataQualityService.runChecks();

        System.exit(SpringApplication.exit(context, () -> 0));
    }
}
