package com.docfitai.backend.reference.geoimport;

import static org.assertj.core.api.Assertions.assertThat;

import com.docfitai.backend.reference.ZipGeography;
import com.docfitai.backend.reference.ZipGeographyRepository;
import com.docfitai.backend.testsupport.PostgresIntegrationSupport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.DefaultResourceLoader;

/**
 * Bounded streaming geography import (CLAUDE.md "Geography Import Pipeline"): a malformed row is
 * skipped without aborting the file, re-running the same file is idempotent, and the source is
 * entirely operator-configured -- constructs the runner directly with a JUnit {@code @TempDir}
 * rather than flipping the enabled flag in the shared Spring context (same convention as
 * {@code ProviderCsvImportRunnerTest}).
 */
class GeographyImportRunnerTest extends PostgresIntegrationSupport {

    private static final String HEADER = "zip_code,city,state_code,county,latitude,longitude\n";

    @Autowired
    private GeographyUpsertService upsertService;

    @Autowired
    private ZipGeographyRepository zipGeographyRepository;

    @TempDir
    Path tempDir;

    @Test
    void importsValidRowsAndSkipsOneBadRowWithoutAbortingTheFile() throws IOException {
        String csv = HEADER
                + "99901,Test City One,CA,Test County,34.000000,-118.000000\n"
                // Malformed: invalid latitude.
                + "99902,Test City Two,CA,Test County,190.0,-118.000000\n"
                + "99903,Test City Three,CA,Test County,34.200000,-118.200000\n";
        Path csvFile = tempDir.resolve("geography.csv");
        Files.writeString(csvFile, csv, StandardCharsets.UTF_8);

        GeographyImportRunner runner = new GeographyImportRunner(
                new GeographyImportProperties(), upsertService, new DefaultResourceLoader());

        GeographyImportRunner.Summary summary = runner.importFrom(csvFile.toUri().toString(), "Test Source", "v1");

        assertThat(summary.recordsRead()).isEqualTo(3);
        assertThat(summary.rowsCreated()).isEqualTo(2);
        assertThat(summary.recordsFailed()).isEqualTo(1);

        assertThat(zipGeographyRepository.findById("99901")).isPresent();
        assertThat(zipGeographyRepository.findById("99903")).isPresent();
        assertThat(zipGeographyRepository.findById("99902")).isEmpty();
    }

    @Test
    void reimportingTheSameFileIsIdempotent() throws IOException {
        String csv = HEADER + "99904,Idempotent City,CA,Test County,34.100000,-118.100000\n";
        Path csvFile = tempDir.resolve("geography-idempotent.csv");
        Files.writeString(csvFile, csv, StandardCharsets.UTF_8);

        GeographyImportRunner runner = new GeographyImportRunner(
                new GeographyImportProperties(), upsertService, new DefaultResourceLoader());

        runner.importFrom(csvFile.toUri().toString(), "Test Source", "v1");
        GeographyImportRunner.Summary second = runner.importFrom(csvFile.toUri().toString(), "Test Source", "v2");

        assertThat(second.rowsCreated()).isZero();
        assertThat(second.rowsUpdated()).isEqualTo(1);
        ZipGeography saved = zipGeographyRepository.findById("99904").orElseThrow();
        assertThat(saved.getSourceVersion()).isEqualTo("v2");
    }

    @Test
    void dryRunCountsWithoutWritingAnything() throws IOException {
        String csv = HEADER + "99905,Dry Run City,CA,Test County,34.300000,-118.300000\n";
        Path csvFile = tempDir.resolve("geography-dry-run.csv");
        Files.writeString(csvFile, csv, StandardCharsets.UTF_8);

        GeographyImportRunner runner = new GeographyImportRunner(
                new GeographyImportProperties(), upsertService, new DefaultResourceLoader());

        GeographyImportRunner.Summary summary = runner.importFrom(csvFile.toUri().toString(), "Test Source", "v1", true);

        assertThat(summary.recordsRead()).isEqualTo(1);
        assertThat(summary.rowsCreated()).isEqualTo(1);
        assertThat(zipGeographyRepository.findById("99905")).isEmpty();
    }

    @Test
    void aMissingSourceFileIsSkippedGracefullyNotAnException() throws IOException {
        GeographyImportRunner runner = new GeographyImportRunner(
                new GeographyImportProperties(), upsertService, new DefaultResourceLoader());

        GeographyImportRunner.Summary summary =
                runner.importFrom(tempDir.resolve("does-not-exist.csv").toUri().toString(), "Test Source", "v1");

        assertThat(summary.recordsRead()).isZero();
    }
}
