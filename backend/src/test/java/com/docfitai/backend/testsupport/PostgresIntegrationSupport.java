package com.docfitai.backend.testsupport;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared Testcontainers Postgres instance (singleton-container pattern) so every
 * Spring Boot integration test in the module runs against the same migrated
 * database instead of each test class starting its own container.
 */
@SpringBootTest
public abstract class PostgresIntegrationSupport {

    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    /**
     * Inserts an individual provider with one primary practice location (fixture shorthand used
     * across provider/insurance tests since Provider Data Platform V2 moved addresses out of the
     * {@code provider} table). Returns the new provider id.
     */
    protected Long insertProviderWithLocation(
            JdbcTemplate jdbcTemplate,
            String npi,
            String firstName,
            String lastName,
            String addressLine1,
            String city,
            String stateCode,
            String postalCode,
            String phone,
            Double latitude,
            Double longitude) {
        jdbcTemplate.update(
                "INSERT INTO provider (npi_number, entity_type, first_name, last_name) VALUES (?, 'INDIVIDUAL', ?, ?)",
                npi, firstName, lastName);
        Long providerId = jdbcTemplate.queryForObject("SELECT id FROM provider WHERE npi_number = ?", Long.class, npi);
        jdbcTemplate.update(
                "INSERT INTO provider_location (provider_id, address_purpose, address_line_1, city, state_code, "
                        + "postal_code, phone, latitude, longitude, coordinate_precision, is_primary, normalized_key) "
                        + "VALUES (?, 'LOCATION', ?, ?, ?, ?, ?, ?, ?, 'ZIP_CENTROID', TRUE, ?)",
                providerId, addressLine1, city, stateCode, postalCode, phone, latitude, longitude, npi + "-primary");
        return providerId;
    }
}
