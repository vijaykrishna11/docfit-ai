package com.docfitai.backend.provider.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.docfitai.backend.testsupport.PostgresIntegrationSupport;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** CLAUDE.md "Change Event Summary": a human-readable count per change type for one import, not a raw event dump. */
class ProviderChangeSummaryServiceTest extends PostgresIntegrationSupport {

    @Autowired
    private ProviderChangeSummaryService summaryService;

    @Autowired
    private ProviderChangeEventRepository changeEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void summarizesMultipleChangeTypesIntoOneHumanReadableLine() {
        Long providerId = jdbcTemplate.queryForObject(
                "INSERT INTO provider (npi_number, entity_type, first_name, last_name) VALUES ('9700000001', 'INDIVIDUAL', 'Change', 'Summary') RETURNING id",
                Long.class);
        Long importId = jdbcTemplate.queryForObject(
                "INSERT INTO data_import (source, started_at, status, records_read, providers_created, providers_updated, "
                        + "locations_created, locations_updated, records_failed) VALUES ('TEST', now(), 'RUNNING', 0, 0, 0, 0, 0, 0) RETURNING id",
                Long.class);

        changeEventRepository.save(new ProviderChangeEvent(providerId, ChangeType.LOCATION_ADDED, null, null, "new", importId, Instant.now()));
        changeEventRepository.save(new ProviderChangeEvent(providerId, ChangeType.LOCATION_ADDED, null, null, "new2", importId, Instant.now()));
        changeEventRepository.save(new ProviderChangeEvent(providerId, ChangeType.PHONE_CHANGED, null, "old", "new", importId, Instant.now()));
        changeEventRepository.save(new ProviderChangeEvent(providerId, ChangeType.PROVIDER_NAME_CHANGED, null, "Old Name", "New Name", importId, Instant.now()));

        ProviderChangeSummaryService.ChangeSummary summary = summaryService.summarize(importId);

        assertThat(summary.countsByType()).containsEntry(ChangeType.LOCATION_ADDED, 2);
        assertThat(summary.countsByType()).containsEntry(ChangeType.PHONE_CHANGED, 1);
        assertThat(summary.countsByType()).containsEntry(ChangeType.PROVIDER_NAME_CHANGED, 1);
        assertThat(summary.toHumanSummary())
                .isEqualTo("2 locations added, 1 phone number changed, 1 provider name changed");
    }

    @Test
    void anImportWithNoChangesSummarizesHonestly() {
        Long importId = jdbcTemplate.queryForObject(
                "INSERT INTO data_import (source, started_at, status, records_read, providers_created, providers_updated, "
                        + "locations_created, locations_updated, records_failed) VALUES ('TEST', now(), 'RUNNING', 0, 0, 0, 0, 0, 0) RETURNING id",
                Long.class);

        ProviderChangeSummaryService.ChangeSummary summary = summaryService.summarize(importId);

        assertThat(summary.countsByType()).isEmpty();
        assertThat(summary.toHumanSummary()).isEqualTo("No tracked changes");
    }
}
