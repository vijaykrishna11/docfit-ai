package com.docfitai.backend.provider.ingestion;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Post-import data quality checks (CLAUDE.md 25, "Data Quality Framework"). Advisory only -- never
 * rejects or deletes data (CLAUDE.md 77: "do not reject valid data solely because some optional
 * fields are missing"), just logs a structured summary an operator can act on. Deliberately small:
 * it does not persist a separate report table, since nothing yet consumes one beyond the log line
 * and the returned {@link QualityReport}.
 */
@Service
public class ProviderDataQualityService {

    private static final Logger log = LoggerFactory.getLogger(ProviderDataQualityService.class);

    private final JdbcTemplate jdbcTemplate;

    public ProviderDataQualityService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public QualityReport runChecks() {
        int providersWithoutDisplayName = count(
                "SELECT count(*) FROM provider WHERE organization_name IS NULL AND first_name IS NULL AND last_name IS NULL");
        int providersWithoutTaxonomy = count(
                "SELECT count(*) FROM provider p WHERE NOT EXISTS (SELECT 1 FROM provider_taxonomy pt WHERE pt.provider_id = p.id)");
        int providersWithoutLocation = count(
                "SELECT count(*) FROM provider p WHERE NOT EXISTS (SELECT 1 FROM provider_location pl WHERE pl.provider_id = p.id)");
        int locationsMissingPostalCode = count("SELECT count(*) FROM provider_location WHERE postal_code IS NULL OR postal_code = ''");
        int locationsWithInvalidLatitude = count(
                "SELECT count(*) FROM provider_location WHERE latitude IS NOT NULL AND (latitude < -90 OR latitude > 90)");
        int locationsWithInvalidLongitude = count(
                "SELECT count(*) FROM provider_location WHERE longitude IS NOT NULL AND (longitude < -180 OR longitude > 180)");
        int invalidNpiFormat = count("SELECT count(*) FROM provider WHERE npi_number !~ '^[0-9]{10}$'");
        int locationsMissingPhone = count("SELECT count(*) FROM provider_location WHERE phone IS NULL OR phone = ''");

        List<QualityFinding> findings = new ArrayList<>();
        addIfNonZero(findings, "invalid_npi_format", QualitySeverity.ERROR, invalidNpiFormat);
        addIfNonZero(findings, "providers_without_display_name", QualitySeverity.ERROR, providersWithoutDisplayName);
        addIfNonZero(findings, "locations_with_invalid_latitude", QualitySeverity.ERROR, locationsWithInvalidLatitude);
        addIfNonZero(findings, "locations_with_invalid_longitude", QualitySeverity.ERROR, locationsWithInvalidLongitude);
        addIfNonZero(findings, "providers_without_taxonomy", QualitySeverity.WARNING, providersWithoutTaxonomy);
        addIfNonZero(findings, "providers_without_location", QualitySeverity.WARNING, providersWithoutLocation);
        addIfNonZero(findings, "locations_missing_postal_code", QualitySeverity.WARNING, locationsMissingPostalCode);
        // Missing phone is INFO, not WARNING: a real, common, non-defective state for many NPPES
        // records (CLAUDE.md 77's own example -- "missing fax: do not even treat as issue").
        addIfNonZero(findings, "locations_missing_phone", QualitySeverity.INFO, locationsMissingPhone);

        QualityReport report = new QualityReport(
                providersWithoutDisplayName,
                providersWithoutTaxonomy,
                providersWithoutLocation,
                locationsMissingPostalCode,
                locationsWithInvalidLatitude,
                locationsWithInvalidLongitude,
                List.copyOf(findings));
        log.info(
                "Provider data quality report: withoutDisplayName={} withoutTaxonomy={} withoutLocation={} "
                        + "locationsMissingPostal={} invalidLatitude={} invalidLongitude={} invalidNpiFormat={} "
                        + "errors={} warnings={} info={}",
                report.providersWithoutDisplayName(),
                report.providersWithoutTaxonomy(),
                report.providersWithoutLocation(),
                report.locationsMissingPostalCode(),
                report.locationsWithInvalidLatitude(),
                report.locationsWithInvalidLongitude(),
                invalidNpiFormat,
                countBySeverity(findings, QualitySeverity.ERROR),
                countBySeverity(findings, QualitySeverity.WARNING),
                countBySeverity(findings, QualitySeverity.INFO));
        return report;
    }

    private void addIfNonZero(List<QualityFinding> findings, String check, QualitySeverity severity, int count) {
        if (count > 0) {
            findings.add(new QualityFinding(check, severity, count));
        }
    }

    private static long countBySeverity(List<QualityFinding> findings, QualitySeverity severity) {
        return findings.stream().filter(f -> f.severity() == severity).count();
    }

    private int count(String sql) {
        Integer result = jdbcTemplate.queryForObject(sql, Integer.class);
        return result == null ? 0 : result;
    }

    public record QualityFinding(String check, QualitySeverity severity, int count) {
    }

    public record QualityReport(
            int providersWithoutDisplayName,
            int providersWithoutTaxonomy,
            int providersWithoutLocation,
            int locationsMissingPostalCode,
            int locationsWithInvalidLatitude,
            int locationsWithInvalidLongitude,
            List<QualityFinding> findings) {
    }
}
