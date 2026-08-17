package com.docfitai.backend.reference;

import static org.assertj.core.api.Assertions.assertThat;

import com.docfitai.backend.testsupport.PostgresIntegrationSupport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Locks the currently-approved specialty set (CLAUDE.md "Specialty Expansion" -- 19 categories as
 * of the data-expansion phase, each backed by an individually-verified NUCC taxonomy code; see
 * docs/specialty-taxonomy-map.md for the source-by-source record). Table-driven per CLAUDE.md 135
 * ("mapping-level unit tests" rather than one enormous integration case per category).
 */
class SpecialtySeedValidationTest extends PostgresIntegrationSupport {

    private static final Map<String, List<String>> EXPECTED_MAPPINGS = Map.ofEntries(
            Map.entry("PRIMARY_CARE", List.of("207Q00000X", "208D00000X", "207R00000X")),
            Map.entry("CARDIOLOGY", List.of("207RC0000X", "207RI0011X")),
            Map.entry("DERMATOLOGY", List.of("207N00000X")),
            Map.entry("ORTHOPEDICS", List.of("207X00000X", "207XX0005X")),
            Map.entry(
                    "PSYCHIATRY_MENTAL_HEALTH",
                    List.of("2084P0800X", "103TC0700X", "1041C0700X", "101YM0800X", "106H00000X")),
            Map.entry("PEDIATRICS", List.of("208000000X")),
            Map.entry("OBSTETRICS_GYNECOLOGY", List.of("207V00000X")),
            Map.entry("NEUROLOGY", List.of("2084N0400X")),
            Map.entry("GASTROENTEROLOGY", List.of("207RG0100X")),
            Map.entry("ENDOCRINOLOGY", List.of("207RE0101X")),
            Map.entry("PULMONOLOGY", List.of("207RP1001X")),
            Map.entry("NEPHROLOGY", List.of("207RN0300X")),
            Map.entry("UROLOGY", List.of("208800000X")),
            Map.entry("OPHTHALMOLOGY", List.of("207W00000X")),
            Map.entry("OTOLARYNGOLOGY", List.of("207Y00000X")),
            Map.entry("ALLERGY_IMMUNOLOGY", List.of("207K00000X")),
            Map.entry("RHEUMATOLOGY", List.of("207RR0500X")),
            Map.entry("GENERAL_SURGERY", List.of("208600000X")),
            Map.entry("PHYSICAL_MEDICINE_REHAB", List.of("208100000X")));

    @Autowired
    private SpecialtyRepository specialtyRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void approvedSpecialtiesArePresentAndOnlyThose() {
        for (String code : EXPECTED_MAPPINGS.keySet()) {
            assertThat(specialtyRepository.findByCode(code)).as("specialty %s should exist", code).isPresent();
        }
        assertThat(specialtyRepository.count()).isEqualTo(EXPECTED_MAPPINGS.size());
    }

    @Test
    void everySpecialtyHasANonBlankNonclinicalDescription() {
        List<String> descriptions = jdbcTemplate.queryForList("SELECT description FROM specialty", String.class);
        assertThat(descriptions).hasSize(EXPECTED_MAPPINGS.size());
        assertThat(descriptions).allSatisfy(description -> assertThat(description).isNotBlank());
        // Nonclinical language check: descriptions must never suggest symptom-based routing.
        assertThat(descriptions).noneMatch(d -> d.toLowerCase().contains("if you have")
                || d.toLowerCase().contains("symptom")
                || d.toLowerCase().contains("choose this if"));
    }

    @ParameterizedTest
    @MethodSource("specialtyCodes")
    void specialtyMapsToExactlyItsApprovedTaxonomyCodes(String specialtyCode) {
        List<String> taxonomyCodes = jdbcTemplate.queryForList(
                "SELECT stm.taxonomy_code FROM specialty_taxonomy_mapping stm "
                        + "JOIN specialty s ON s.id = stm.specialty_id WHERE s.code = ?",
                String.class,
                specialtyCode);
        assertThat(taxonomyCodes).containsExactlyInAnyOrderElementsOf(EXPECTED_MAPPINGS.get(specialtyCode));
    }

    static List<String> specialtyCodes() {
        return List.copyOf(EXPECTED_MAPPINGS.keySet());
    }

    @Test
    void primaryCareExcludesPediatrics() {
        List<String> taxonomyCodes = jdbcTemplate.queryForList(
                "SELECT stm.taxonomy_code FROM specialty_taxonomy_mapping stm "
                        + "JOIN specialty s ON s.id = stm.specialty_id WHERE s.code = 'PRIMARY_CARE'",
                String.class);
        assertThat(taxonomyCodes).doesNotContain("208000000X");
    }
}
