package com.docfitai.backend.provider.ingestion;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Bounded streaming CSV provider importer (CLAUDE.md 28-31). Off by default
 * ({@code docfitai.import.csv.enabled=false}); when enabled, reads every {@code *.csv} file from
 * an operator-configured local directory ({@code docfitai.import.csv.source-directory}) -- never
 * a request-supplied path, so there is no arbitrary-filesystem-read or SSRF-style surface.
 *
 * <p>Streams line by line (never loads a whole file into memory) and commits one small
 * transaction per row via {@link ProviderUpsertService#upsert}, which is itself the natural
 * "bounded transaction size" this needs (CLAUDE.md 33) -- no separate batching/flush logic was
 * added since nothing here justified it (CLAUDE.md 33: "do not tune ... without measuring").
 * A malformed row is logged and skipped; it never aborts the rest of the file (CLAUDE.md 24).
 */
@Component
@ConditionalOnProperty(prefix = "docfitai.import.csv", name = "enabled", havingValue = "true")
public class ProviderCsvImportRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ProviderCsvImportRunner.class);

    private final ProviderCsvImportProperties properties;
    private final ProviderUpsertService upsertService;
    private final DataImportRepository dataImportRepository;
    private final ProviderDataQualityService dataQualityService;
    private final ProviderCsvDryRunService dryRunService;
    private final ProviderChangeSummaryService changeSummaryService;

    public ProviderCsvImportRunner(
            ProviderCsvImportProperties properties,
            ProviderUpsertService upsertService,
            DataImportRepository dataImportRepository,
            ProviderDataQualityService dataQualityService,
            ProviderCsvDryRunService dryRunService,
            ProviderChangeSummaryService changeSummaryService) {
        this.properties = properties;
        this.upsertService = upsertService;
        this.dataImportRepository = dataImportRepository;
        this.dataQualityService = dataQualityService;
        this.dryRunService = dryRunService;
        this.changeSummaryService = changeSummaryService;
    }

    @Override
    public void run(String... args) throws IOException {
        if (properties.getSourceDirectory() == null || properties.getSourceDirectory().isBlank()) {
            log.warn("docfitai.import.csv.enabled=true but no source directory configured (docfitai.import.csv.source-directory) -- skipping.");
            return;
        }
        if (properties.isDryRun()) {
            // Parse/validate/count only -- never writes (CLAUDE.md "Operator Dry-Run").
            dryRunService.analyze(properties.getSourceDirectory());
            return;
        }
        Path directory = Path.of(properties.getSourceDirectory());
        if (!Files.isDirectory(directory)) {
            log.warn("Configured CSV source directory does not exist or is not a directory: {}", directory);
            return;
        }

        DataImport dataImport = dataImportRepository.save(new DataImport("CSV", Instant.now()));

        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.csv")) {
            for (Path file : files) {
                importFile(file, dataImport);
            }
        }

        dataImport.complete(Instant.now());
        dataImportRepository.save(dataImport);
        log.info(
                "CSV import complete: status={} recordsRead={} providersCreated={} providersUpdated={} "
                        + "locationsCreated={} locationsUpdated={} recordsFailed={}",
                dataImport.getStatus(),
                dataImport.getRecordsRead(),
                dataImport.getProvidersCreated(),
                dataImport.getProvidersUpdated(),
                dataImport.getLocationsCreated(),
                dataImport.getLocationsUpdated(),
                dataImport.getRecordsFailed());
        dataQualityService.runChecks();
        log.info("Change summary for this import: {}", changeSummaryService.summarize(dataImport.getId()).toHumanSummary());
    }

    private void importFile(Path file, DataImport dataImport) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                log.warn("CSV file {} is empty -- skipping.", file);
                return;
            }
            List<String> header = ProviderCsvRecordParser.parseHeader(headerLine);

            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                try {
                    ProviderImportRecord record = ProviderCsvRecordParser.parseRow(header, line);
                    var outcome = upsertService.upsert(record, dataImport.getId());
                    dataImport.recordUpsert(outcome);
                } catch (Exception e) {
                    log.warn("Failed to import {}:{} -- {}", file.getFileName(), lineNumber, e.getMessage());
                    dataImport.recordFailure();
                }
            }
        }
    }
}
