package com.docfitai.backend.provider.nppes;

import com.docfitai.backend.provider.ingestion.DataImport;
import com.docfitai.backend.provider.ingestion.DataImportRepository;
import com.docfitai.backend.provider.ingestion.ProviderDataQualityService;
import com.docfitai.backend.provider.ingestion.ProviderImportRecord;
import com.docfitai.backend.provider.ingestion.ProviderUpsertService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Core, connector-agnostic refresh logic (CLAUDE.md "Operator-Triggerable Provider Refresh") --
 * re-fetches a bounded, explicit list of NPIs from NPPES and re-upserts them. Shared by both the
 * operator CLI ({@link NppesRefreshRunner}) and the optional scheduler ({@code
 * ProviderRefreshScheduler}) so the two never drift, and so this one class is all that needs a
 * mocked-connector test (CLAUDE.md "no external dependency in CI").
 *
 * <p>Deliberately narrow: only ever touches the NPIs it's given. Never deactivates or removes a
 * provider/location/taxonomy that isn't reported back by NPPES for a refreshed NPI (CLAUDE.md
 * "Partial Import Safety") -- a provider genuinely deactivated in NPPES since the original import
 * would need a separate, explicit deactivation decision, not a silent side effect of a refresh.
 */
@Service
public class ProviderRefreshService {

    private static final Logger log = LoggerFactory.getLogger(ProviderRefreshService.class);
    // A refresh call is meant for "a bounded, specific list an operator cares about," not a bulk
    // re-import -- this cap exists purely as a safety backstop against a mis-sized config value.
    private static final int MAX_NPIS_PER_RUN = 1000;

    private final NppesClient nppesClient;
    private final NppesRecordFactory recordFactory;
    private final ProviderUpsertService providerUpsertService;
    private final DataImportRepository dataImportRepository;
    private final ProviderDataQualityService dataQualityService;
    private final JdbcTemplate jdbcTemplate;

    public ProviderRefreshService(
            NppesClient nppesClient,
            NppesRecordFactory recordFactory,
            ProviderUpsertService providerUpsertService,
            DataImportRepository dataImportRepository,
            ProviderDataQualityService dataQualityService,
            JdbcTemplate jdbcTemplate) {
        this.nppesClient = nppesClient;
        this.recordFactory = recordFactory;
        this.providerUpsertService = providerUpsertService;
        this.dataImportRepository = dataImportRepository;
        this.dataQualityService = dataQualityService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public RefreshSummary refreshByNpis(List<String> npis) {
        if (npis == null || npis.isEmpty()) {
            log.info("Provider refresh requested with an empty NPI list -- nothing to do.");
            return new RefreshSummary(0, 0, 0, 0, 0);
        }
        List<String> bounded = npis.size() > MAX_NPIS_PER_RUN ? npis.subList(0, MAX_NPIS_PER_RUN) : npis;
        if (bounded.size() < npis.size()) {
            log.warn("Provider refresh NPI list ({} entries) exceeds MAX_NPIS_PER_RUN ({}); only the first {} will be refreshed.",
                    npis.size(), MAX_NPIS_PER_RUN, MAX_NPIS_PER_RUN);
        }

        Set<String> knownTaxonomyCodes =
                Set.copyOf(jdbcTemplate.queryForList("SELECT taxonomy_code FROM npi_taxonomy", String.class));

        DataImport dataImport = new DataImport("NPPES_REFRESH", Instant.now());
        dataImport = dataImportRepository.save(dataImport);

        int notFound = 0;
        int failed = 0;
        try {
            for (String npi : bounded) {
                try {
                    NppesResponse response = nppesClient.lookupByNpi(npi);
                    List<NppesResult> results = response.results() == null ? List.of() : response.results();
                    if (results.isEmpty()) {
                        notFound++;
                        continue;
                    }
                    Optional<NppesProviderMapper.MappedProvider> mapped = NppesProviderMapper.map(results.get(0), knownTaxonomyCodes);
                    if (mapped.isEmpty()) {
                        notFound++;
                        continue;
                    }
                    ProviderImportRecord record = recordFactory.toImportRecord(mapped.get());
                    var outcome = providerUpsertService.upsert(record, dataImport.getId());
                    dataImport.recordUpsert(outcome);
                } catch (Exception e) {
                    // One bad NPI never aborts the whole refresh run (CLAUDE.md "bad source
                    // records fail individually").
                    log.warn("Failed to refresh NPI {}: {}", npi, e.getMessage());
                    dataImport.recordFailure();
                    failed++;
                }
            }
            dataImport.setScope("PARTIAL", "NPPES API refresh by explicit NPI list, %d NPI(s) requested".formatted(bounded.size()));
            dataImport.complete(Instant.now());
        } catch (Exception e) {
            // An unexpected failure outside the per-NPI loop (e.g. the DB itself became
            // unavailable) must still mark the import FAILED rather than leaving it stuck RUNNING
            // forever (CLAUDE.md "failure marks import FAILED/PARTIAL... without crashing search").
            log.error("Provider refresh run failed unexpectedly: {}", e.getMessage(), e);
            dataImport.fail(Instant.now());
            dataImportRepository.save(dataImport);
            throw e;
        }
        dataImportRepository.save(dataImport);
        dataQualityService.runChecks();

        RefreshSummary summary = new RefreshSummary(
                bounded.size(), dataImport.getProvidersCreated(), dataImport.getProvidersUpdated(), notFound, failed);
        log.info(
                "Provider refresh complete: requested={} providersCreated={} providersUpdated={} notFoundOrUnmapped={} failed={}",
                summary.npisRequested(), summary.providersCreated(), summary.providersUpdated(), summary.notFoundOrUnmapped(), summary.failed());
        return summary;
    }

    public record RefreshSummary(int npisRequested, int providersCreated, int providersUpdated, int notFoundOrUnmapped, int failed) {
    }
}
