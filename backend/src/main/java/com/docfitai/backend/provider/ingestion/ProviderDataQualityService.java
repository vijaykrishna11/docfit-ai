package com.docfitai.backend.provider.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Post-import data quality checks (CLAUDE.md 25). Advisory only -- it never rejects or deletes
 * data, it just logs a structured summary an operator can act on. Deliberately small: it does
 * not persist a separate report table, since nothing yet consumes one beyond the log line.
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

        QualityReport report = new QualityReport(
                providersWithoutDisplayName,
                providersWithoutTaxonomy,
                providersWithoutLocation,
                locationsMissingPostalCode,
                locationsWithInvalidLatitude,
                locationsWithInvalidLongitude);
        log.info(
                "Provider data quality report: withoutDisplayName={} withoutTaxonomy={} withoutLocation={} "
                        + "locationsMissingPostal={} invalidLatitude={} invalidLongitude={}",
                report.providersWithoutDisplayName(),
                report.providersWithoutTaxonomy(),
                report.providersWithoutLocation(),
                report.locationsMissingPostalCode(),
                report.locationsWithInvalidLatitude(),
                report.locationsWithInvalidLongitude());
        return report;
    }

    private int count(String sql) {
        Integer result = jdbcTemplate.queryForObject(sql, Integer.class);
        return result == null ? 0 : result;
    }

    public record QualityReport(
            int providersWithoutDisplayName,
            int providersWithoutTaxonomy,
            int providersWithoutLocation,
            int locationsMissingPostalCode,
            int locationsWithInvalidLatitude,
            int locationsWithInvalidLongitude) {
    }
}
