package com.docfitai.backend.provider.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.docfitai.backend.provider.Provider;
import com.docfitai.backend.provider.ProviderLocationRepository;
import com.docfitai.backend.provider.ProviderRepository;
import com.docfitai.backend.testsupport.PostgresIntegrationSupport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Bounded streaming CSV import (CLAUDE.md 29, 53): multi-location rows for the same NPI produce
 * multiple locations on one provider, a malformed row is skipped without aborting the file, and
 * the source directory is entirely operator-configured (never request input, CLAUDE.md 30) --
 * this test constructs the runner directly with a JUnit {@code @TempDir} rather than flipping the
 * enabled flag in the shared Spring context.
 */
class ProviderCsvImportRunnerTest extends PostgresIntegrationSupport {

    private static final String HEADER =
            "npi,entity_type,first_name,last_name,organization_name,address_line_1,address_line_2,city,state_code,postal_code,phone,fax,latitude,longitude,taxonomy_codes\n";

    @Autowired
    private ProviderUpsertService upsertService;

    @Autowired
    private DataImportRepository dataImportRepository;

    @Autowired
    private ProviderDataQualityService dataQualityService;

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private ProviderLocationRepository providerLocationRepository;

    @TempDir
    Path tempDir;

    @Test
    void importsMultiLocationProviderAndSkipsOneBadRowWithoutAbortingTheFile() throws IOException {
        String csv = HEADER
                + "6500000001,INDIVIDUAL,Csv,Provider,,1 Office A,,Long Beach,CA,90802,562-555-0001,,33.77,-118.19,207RC0000X\n"
                + "6500000001,INDIVIDUAL,Csv,Provider,,2 Office B,,Lakewood,CA,90712,562-555-0002,,33.85,-118.13,207RC0000X\n"
                // Malformed: missing address_line_1 -- must be skipped, not abort the file.
                + "6500000002,INDIVIDUAL,Bad,Row,,,,,CA,90802,,,,,207RC0000X\n"
                + "6500000003,ORGANIZATION,,,Csv Medical Group,1 Group Way,,Long Beach,CA,90802,,,,,207RC0000X\n";
        Path csvFile = tempDir.resolve("providers.csv");
        Files.writeString(csvFile, csv, StandardCharsets.UTF_8);

        ProviderCsvImportProperties properties = new ProviderCsvImportProperties();
        properties.setEnabled(true);
        properties.setSourceDirectory(tempDir.toString());
        ProviderCsvImportRunner runner =
                new ProviderCsvImportRunner(properties, upsertService, dataImportRepository, dataQualityService);

        runner.run();

        Provider multiLocationProvider = providerRepository.findByNpiNumber("6500000001").orElseThrow();
        assertThat(providerLocationRepository.findByProviderIdOrderByPrimaryDescId(multiLocationProvider.getId())).hasSize(2);

        assertThat(providerRepository.findByNpiNumber("6500000002")).isEmpty();

        Provider organizationProvider = providerRepository.findByNpiNumber("6500000003").orElseThrow();
        assertThat(organizationProvider.getOrganizationName()).isEqualTo("Csv Medical Group");

        List<DataImport> imports = dataImportRepository.findAll();
        assertThat(imports).anySatisfy(dataImport -> {
            if ("CSV".equals(dataImport.getSource())) {
                assertThat(dataImport.getRecordsFailed()).isGreaterThanOrEqualTo(1);
                assertThat(dataImport.getStatus()).isIn(DataImportStatus.PARTIAL, DataImportStatus.COMPLETED);
            }
        });
    }
}
