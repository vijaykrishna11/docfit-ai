package com.docfitai.backend.provider.ingestion;

import com.docfitai.backend.provider.ProviderRepository;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Operator dry-run for the CSV provider import path (CLAUDE.md "Operator Dry-Run"): parses,
 * validates, and counts every row exactly like a real import would -- but never calls {@link
 * ProviderUpsertService}, so nothing is written. Create/update estimates are computed from a
 * read-only {@code existsByNpiNumber} lookup per distinct NPI, which is reliably calculable
 * without writing; anything not reliably calculable this way (e.g. exact new-location counts,
 * which depend on per-location dedup logic inside the real upsert) is deliberately left out rather
 * than faked.
 */
@Service
public class ProviderCsvDryRunService {

    private static final Logger log = LoggerFactory.getLogger(ProviderCsvDryRunService.class);

    private final ProviderRepository providerRepository;
    private final JdbcTemplate jdbcTemplate;

    public ProviderCsvDryRunService(ProviderRepository providerRepository, JdbcTemplate jdbcTemplate) {
        this.providerRepository = providerRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public DryRunReport analyze(String sourceDirectory) throws IOException {
        Path directory = Path.of(sourceDirectory);
        if (!Files.isDirectory(directory)) {
            log.warn("Configured CSV source directory does not exist or is not a directory: {}", directory);
            return new DryRunReport(0, 0, 0, 0, 0, 0, 0, 0);
        }

        Set<String> knownTaxonomyCodes =
                Set.copyOf(jdbcTemplate.queryForList("SELECT taxonomy_code FROM npi_taxonomy", String.class));

        int recordsRead = 0;
        int validRecords = 0;
        int invalidRecords = 0;
        int recognizedTaxonomyCodes = 0;
        int unrecognizedTaxonomyCodes = 0;
        Set<String> distinctNpis = new HashSet<>();

        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.csv")) {
            for (Path file : files) {
                try (BufferedReader reader = Files.newBufferedReader(file)) {
                    String headerLine = reader.readLine();
                    if (headerLine == null) {
                        continue;
                    }
                    List<String> header = ProviderCsvRecordParser.parseHeader(headerLine);
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.isBlank()) {
                            continue;
                        }
                        recordsRead++;
                        try {
                            ProviderImportRecord record = ProviderCsvRecordParser.parseRow(header, line);
                            validRecords++;
                            distinctNpis.add(record.identity().npiNumber());
                            for (ProviderTaxonomyRecord taxonomy : record.taxonomies()) {
                                if (knownTaxonomyCodes.contains(taxonomy.taxonomyCode())) {
                                    recognizedTaxonomyCodes++;
                                } else {
                                    unrecognizedTaxonomyCodes++;
                                }
                            }
                        } catch (Exception e) {
                            invalidRecords++;
                        }
                    }
                }
            }
        }

        int estimatedNewProviders = (int) distinctNpis.stream().filter(npi -> !providerRepository.existsByNpiNumber(npi)).count();
        int estimatedUpdatedProviders = distinctNpis.size() - estimatedNewProviders;

        DryRunReport report = new DryRunReport(
                recordsRead,
                validRecords,
                invalidRecords,
                distinctNpis.size(),
                estimatedNewProviders,
                estimatedUpdatedProviders,
                recognizedTaxonomyCodes,
                unrecognizedTaxonomyCodes);
        log.info(
                "CSV dry run complete (no data written): recordsRead={} valid={} invalid={} distinctNpis={} "
                        + "estimatedNewProviders={} estimatedUpdatedProviders={} recognizedTaxonomyCodes={} unrecognizedTaxonomyCodes={}",
                report.recordsRead(), report.validRecords(), report.invalidRecords(), report.distinctNpis(),
                report.estimatedNewProviders(), report.estimatedUpdatedProviders(), report.recognizedTaxonomyCodes(),
                report.unrecognizedTaxonomyCodes());
        return report;
    }

    public record DryRunReport(
            int recordsRead,
            int validRecords,
            int invalidRecords,
            int distinctNpis,
            int estimatedNewProviders,
            int estimatedUpdatedProviders,
            int recognizedTaxonomyCodes,
            int unrecognizedTaxonomyCodes) {
    }
}
