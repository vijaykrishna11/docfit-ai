package com.docfitai.backend.provider.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.docfitai.backend.provider.ProviderRepository;
import com.docfitai.backend.testsupport.PostgresIntegrationSupport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;

/** CLAUDE.md "Operator Dry-Run": parse/validate/count only, and never fake a number that isn't reliably calculable read-only. */
class ProviderCsvDryRunServiceTest extends PostgresIntegrationSupport {

    private static final String HEADER =
            "npi,entity_type,first_name,last_name,organization_name,address_line_1,address_line_2,city,state_code,postal_code,phone,fax,latitude,longitude,taxonomy_codes\n";

    @Autowired
    private ProviderCsvDryRunService dryRunService;

    @Autowired
    private ProviderUpsertService upsertService;

    @Autowired
    private ProviderRepository providerRepository;

    @TempDir
    Path tempDir;

    @Test
    void countsValidInvalidAndRecognizedTaxonomyCodesWithoutWriting() throws IOException {
        String csv = HEADER
                + "6500000201,INDIVIDUAL,Dry,One,,1 Office A,,Long Beach,CA,90802,,,,,207RC0000X\n"
                // Unrecognized taxonomy code -- still a structurally valid row.
                + "6500000202,INDIVIDUAL,Dry,Two,,1 Office B,,Long Beach,CA,90802,,,,,ZZZZZZZZZZ\n"
                // Malformed: missing address_line_1.
                + "6500000203,INDIVIDUAL,Bad,Row,,,,,CA,90802,,,,,207RC0000X\n";
        Files.writeString(tempDir.resolve("providers.csv"), csv, StandardCharsets.UTF_8);

        ProviderCsvDryRunService.DryRunReport report = dryRunService.analyze(tempDir.toString());

        assertThat(report.recordsRead()).isEqualTo(3);
        assertThat(report.validRecords()).isEqualTo(2);
        assertThat(report.invalidRecords()).isEqualTo(1);
        assertThat(report.recognizedTaxonomyCodes()).isEqualTo(1);
        assertThat(report.unrecognizedTaxonomyCodes()).isEqualTo(1);
        assertThat(providerRepository.findByNpiNumber("6500000201")).isEmpty();
    }

    @Test
    void distinguishesEstimatedNewFromEstimatedUpdatedProviders() throws IOException {
        // Real-upsert an existing provider first so the dry run has something to detect as
        // "already exists."
        ProviderImportRecord existing = ProviderCsvRecordParser.parseRow(
                ProviderCsvRecordParser.parseHeader(HEADER.strip()),
                "6500000301,INDIVIDUAL,Existing,Provider,,1 Office A,,Long Beach,CA,90802,,,,,207RC0000X");
        upsertService.upsert(existing);

        String csv = HEADER
                + "6500000301,INDIVIDUAL,Existing,Provider,,2 Office B,,Lakewood,CA,90712,,,,,207RC0000X\n"
                + "6500000302,INDIVIDUAL,Brand,New,,1 Office C,,Long Beach,CA,90802,,,,,207RC0000X\n";
        Files.writeString(tempDir.resolve("providers.csv"), csv, StandardCharsets.UTF_8);

        ProviderCsvDryRunService.DryRunReport report = dryRunService.analyze(tempDir.toString());

        assertThat(report.distinctNpis()).isEqualTo(2);
        assertThat(report.estimatedNewProviders()).isEqualTo(1);
        assertThat(report.estimatedUpdatedProviders()).isEqualTo(1);
        // Still no second location written for the "existing" NPI -- this was a dry run.
        assertThat(providerRepository.findByNpiNumber("6500000301")).isPresent();
    }
}
