package com.docfitai.backend.reference.geoimport;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * Bounded, operator-triggered geography reference import (CLAUDE.md "Geography Import Pipeline").
 * Off by default ({@code docfitai.import.geography.enabled=false}). {@code sourcePath} is a Spring
 * resource location resolved server-side only -- {@code classpath:...} for the bundled,
 * source-verified LA County reference set, or a plain filesystem path for an operator-supplied
 * file -- never accepted from a request (CLAUDE.md "File Security").
 *
 * <p>Streams the file line by line (never loads it whole into memory) and upserts each row via
 * {@link GeographyUpsertService} -- the same "small bounded transaction, one bad row never aborts
 * the run" posture as the provider CSV importer.
 */
@Component
@ConditionalOnProperty(prefix = "docfitai.import.geography", name = "enabled", havingValue = "true")
public class GeographyImportRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(GeographyImportRunner.class);

    private final GeographyImportProperties properties;
    private final GeographyUpsertService upsertService;
    private final ResourceLoader resourceLoader;

    public GeographyImportRunner(
            GeographyImportProperties properties, GeographyUpsertService upsertService, ResourceLoader resourceLoader) {
        this.properties = properties;
        this.upsertService = upsertService;
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void run(String... args) throws IOException {
        Summary summary = importFrom(properties.getSourcePath(), properties.getSourceName(), properties.getSourceVersion());
        log.info(
                "Geography import complete: source={} recordsRead={} rowsCreated={} rowsUpdated={} recordsFailed={}",
                properties.getSourcePath(),
                summary.recordsRead(),
                summary.rowsCreated(),
                summary.rowsUpdated(),
                summary.recordsFailed());
    }

    /** Core logic, separated from {@link #run} so it's directly unit-testable without CommandLineRunner ceremony. */
    public Summary importFrom(String sourcePath, String sourceName, String sourceVersion) throws IOException {
        Resource resource = resourceLoader.getResource(sourcePath);
        if (!resource.exists()) {
            log.warn("Configured geography source does not exist: {} -- skipping.", sourcePath);
            return new Summary(0, 0, 0, 0);
        }

        int recordsRead = 0;
        int rowsCreated = 0;
        int rowsUpdated = 0;
        int recordsFailed = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                log.warn("Geography source {} is empty -- skipping.", sourcePath);
                return new Summary(0, 0, 0, 0);
            }
            List<String> header = GeographyRecordParser.parseHeader(headerLine);

            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                recordsRead++;
                try {
                    GeographyRecord record = GeographyRecordParser.parseRow(header, line);
                    boolean created = upsertService.upsert(record, sourceName, sourceVersion);
                    if (created) {
                        rowsCreated++;
                    } else {
                        rowsUpdated++;
                    }
                } catch (Exception e) {
                    log.warn("Failed to import geography row {}:{} -- {}", sourcePath, lineNumber, e.getMessage());
                    recordsFailed++;
                }
            }
        }

        return new Summary(recordsRead, rowsCreated, rowsUpdated, recordsFailed);
    }

    public record Summary(int recordsRead, int rowsCreated, int rowsUpdated, int recordsFailed) {
    }
}
