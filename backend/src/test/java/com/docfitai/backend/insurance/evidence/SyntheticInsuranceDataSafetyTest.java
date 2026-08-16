package com.docfitai.backend.insurance.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import com.docfitai.backend.testsupport.PostgresIntegrationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Production safety regression (CLAUDE.md 26-27, 62): with
 * {@code docfitai.insurance.synthetic-demo.enabled} absent (its default, exactly what a real
 * production/dev startup looks like -- this whole test module never sets that property),
 * DemoNetworkEvidenceSeeder must not even exist as a bean, and no synthetic evidence rows may
 * exist for a provider this test itself never touched.
 */
class SyntheticInsuranceDataSafetyTest extends PostgresIntegrationSupport {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void demoSeederBeanDoesNotExistWithoutExplicitOptIn() {
        assertThat(applicationContext.getBeanNamesForType(DemoNetworkEvidenceSeeder.class)).isEmpty();
    }

    @Test
    void aFreshlyInsertedProviderHasNoSyntheticEvidenceSeededAutomatically() {
        String npi = "9000000001";
        jdbcTemplate.update("INSERT INTO provider (npi_number, entity_type, first_name, last_name) VALUES (?, 'INDIVIDUAL', 'NoSeed', 'Doctor')", npi);
        Long providerId = jdbcTemplate.queryForObject("SELECT id FROM provider WHERE npi_number = ?", Long.class, npi);

        Integer evidenceRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM provider_network_evidence WHERE provider_id = ?", Integer.class, providerId);
        assertThat(evidenceRows).isZero();
    }
}
