package com.docfitai.backend.provider.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.docfitai.backend.testsupport.PostgresIntegrationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** CLAUDE.md "Data Quality Framework" / "Quality Severity": advisory only, classified by severity, never a rejection. */
class ProviderDataQualityServiceTest extends PostgresIntegrationSupport {

    @Autowired
    private ProviderDataQualityService qualityService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void anInvalidNpiFormatIsAnErrorSeverityFinding() {
        jdbcTemplate.update("INSERT INTO provider (npi_number, entity_type, first_name, last_name) VALUES (?, 'INDIVIDUAL', ?, ?)",
                "12345ABCDE", "Quality", "Test");

        ProviderDataQualityService.QualityReport report = qualityService.runChecks();

        assertThat(report.findings())
                .anySatisfy(finding -> {
                    assertThat(finding.check()).isEqualTo("invalid_npi_format");
                    assertThat(finding.severity()).isEqualTo(QualitySeverity.ERROR);
                    assertThat(finding.count()).isGreaterThanOrEqualTo(1);
                });
    }

    @Test
    void aValidTenDigitNpiIsNeverFlaggedAsInvalid() {
        jdbcTemplate.update("INSERT INTO provider (npi_number, entity_type, first_name, last_name) VALUES (?, 'INDIVIDUAL', ?, ?)",
                "9900000001", "Valid", "Npi");

        ProviderDataQualityService.QualityReport report = qualityService.runChecks();

        // A valid NPI must never itself contribute to the invalid-format count -- other
        // pre-existing fixture rows in this shared-database suite may still trip it, so this
        // asserts our own row doesn't add to it rather than asserting the count is exactly zero.
        long before = report.findings().stream()
                .filter(f -> f.check().equals("invalid_npi_format"))
                .mapToLong(ProviderDataQualityService.QualityFinding::count)
                .findFirst()
                .orElse(0);
        jdbcTemplate.update("DELETE FROM provider WHERE npi_number = '9900000001'");
        long after = qualityService.runChecks().findings().stream()
                .filter(f -> f.check().equals("invalid_npi_format"))
                .mapToLong(ProviderDataQualityService.QualityFinding::count)
                .findFirst()
                .orElse(0);
        assertThat(after).isEqualTo(before);
    }

    @Test
    void missingPhoneIsInfoSeverityNeverErrorOrWarning() {
        Long providerId = jdbcTemplate.queryForObject(
                "INSERT INTO provider (npi_number, entity_type, first_name, last_name) VALUES ('9900000002', 'INDIVIDUAL', 'No', 'Phone') RETURNING id",
                Long.class);
        jdbcTemplate.update(
                "INSERT INTO provider_location (provider_id, address_purpose, address_line_1, city, state_code, postal_code, "
                        + "coordinate_precision, is_primary, normalized_key) VALUES (?, 'LOCATION', '1 Test St', 'Long Beach', 'CA', "
                        + "'90802', 'ZIP_CENTROID', TRUE, ?)",
                providerId, "9900000002-primary");

        ProviderDataQualityService.QualityReport report = qualityService.runChecks();

        assertThat(report.findings())
                .filteredOn(f -> f.check().equals("locations_missing_phone"))
                .allSatisfy(f -> assertThat(f.severity()).isEqualTo(QualitySeverity.INFO));
    }
}
