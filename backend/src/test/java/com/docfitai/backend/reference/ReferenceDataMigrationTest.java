package com.docfitai.backend.reference;

import static org.assertj.core.api.Assertions.assertThat;

import com.docfitai.backend.testsupport.PostgresIntegrationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

class ReferenceDataMigrationTest extends PostgresIntegrationSupport {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoadsAndFlywayMigratesAllReferenceTables() {
        assertThat(applicationContext).isNotNull();

        for (String table : new String[] {
            "specialty", "npi_taxonomy", "specialty_taxonomy_mapping", "zip_geography", "insurance_carrier"
        }) {
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
            assertThat(count).as("row count for table %s", table).isNotNull().isGreaterThan(0);
        }
    }
}
