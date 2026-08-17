package com.docfitai.backend.reference.geoimport;

import static org.assertj.core.api.Assertions.assertThat;

import com.docfitai.backend.reference.ZipGeography;
import com.docfitai.backend.reference.ZipGeographyRepository;
import com.docfitai.backend.testsupport.PostgresIntegrationSupport;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** CLAUDE.md "Geography Unique Key": re-importing the same ZIP updates in place, never duplicates. */
class GeographyUpsertServiceTest extends PostgresIntegrationSupport {

    @Autowired
    private GeographyUpsertService upsertService;

    @Autowired
    private ZipGeographyRepository zipGeographyRepository;

    @Test
    void createsANewRowAndStampsProvenance() {
        GeographyRecord record =
                new GeographyRecord("99801", "Test City", "CA", "Test County", new BigDecimal("34.0"), new BigDecimal("-118.0"));

        boolean created = upsertService.upsert(record, "Test Source", "v1");

        assertThat(created).isTrue();
        ZipGeography saved = zipGeographyRepository.findById("99801").orElseThrow();
        assertThat(saved.getCity()).isEqualTo("Test City");
        assertThat(saved.getCounty()).isEqualTo("Test County");
        assertThat(saved.getSourceName()).isEqualTo("Test Source");
        assertThat(saved.getSourceVersion()).isEqualTo("v1");
        assertThat(saved.getSourceImportedAt()).isNotNull();
    }

    @Test
    void reimportingTheSameZipUpdatesInPlaceRatherThanDuplicating() {
        GeographyRecord original =
                new GeographyRecord("99802", "Old City", "CA", "Old County", new BigDecimal("34.0"), new BigDecimal("-118.0"));
        upsertService.upsert(original, "Source A", "v1");

        GeographyRecord updated =
                new GeographyRecord("99802", "New City", "CA", "New County", new BigDecimal("35.0"), new BigDecimal("-119.0"));
        boolean created = upsertService.upsert(updated, "Source B", "v2");

        assertThat(created).isFalse();
        ZipGeography saved = zipGeographyRepository.findById("99802").orElseThrow();
        assertThat(saved.getCity()).isEqualTo("New City");
        assertThat(saved.getCounty()).isEqualTo("New County");
        assertThat(saved.getSourceName()).isEqualTo("Source B");
        assertThat(zipGeographyRepository.count()).isGreaterThanOrEqualTo(1);
    }
}
