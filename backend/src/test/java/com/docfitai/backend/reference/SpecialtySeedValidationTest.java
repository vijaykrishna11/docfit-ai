package com.docfitai.backend.reference;

import static org.assertj.core.api.Assertions.assertThat;

import com.docfitai.backend.testsupport.PostgresIntegrationSupport;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class SpecialtySeedValidationTest extends PostgresIntegrationSupport {

    @Autowired
    private SpecialtyRepository specialtyRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void approvedMvpSpecialtiesArePresentAndOnlyThose() {
        assertThat(specialtyRepository.findByCode("PRIMARY_CARE")).isPresent();
        assertThat(specialtyRepository.findByCode("CARDIOLOGY")).isPresent();
        assertThat(specialtyRepository.findByCode("DERMATOLOGY")).isPresent();
        assertThat(specialtyRepository.findByCode("ORTHOPEDICS")).isPresent();
        assertThat(specialtyRepository.findByCode("PSYCHIATRY_MENTAL_HEALTH")).isPresent();
        assertThat(specialtyRepository.count()).isEqualTo(5);
    }

    @Test
    void primaryCareExcludesPediatricsAndUsesOnlyApprovedTaxonomyCodes() {
        List<String> taxonomyCodes = jdbcTemplate.queryForList(
                "SELECT stm.taxonomy_code FROM specialty_taxonomy_mapping stm "
                        + "JOIN specialty s ON s.id = stm.specialty_id WHERE s.code = 'PRIMARY_CARE'",
                String.class);

        assertThat(taxonomyCodes).containsExactlyInAnyOrder("207Q00000X", "208D00000X", "207R00000X");
        assertThat(taxonomyCodes).doesNotContain("208000000X");
    }
}
