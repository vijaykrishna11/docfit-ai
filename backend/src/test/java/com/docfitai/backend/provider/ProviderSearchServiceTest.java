package com.docfitai.backend.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static java.util.stream.Collectors.toMap;

import com.docfitai.backend.provider.dto.ProviderSearchResponseDto;
import com.docfitai.backend.provider.dto.ProviderSearchResultDto;
import com.docfitai.backend.testsupport.PostgresIntegrationSupport;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class ProviderSearchServiceTest extends PostgresIntegrationSupport {

    private static final String NEAR_NPI = "1000000001";
    private static final String FAR_IN_RADIUS_NPI = "1000000002";
    private static final String TOO_FAR_NPI = "1000000003";
    private static final String WRONG_SPECIALTY_NPI = "1000000004";

    @Autowired
    private ProviderSearchService providerSearchService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void searchFiltersBySpecialtyAndRadiusAndSortsNearestFirst() {
        insertProvider(NEAR_NPI, "Near", "Doctor", "90802", "33.770000", "-118.191000");
        insertTaxonomy(NEAR_NPI, "207RC0000X", true);

        insertProvider(FAR_IN_RADIUS_NPI, "FarButIn", "Radius", "90815", "33.794000", "-118.116000");
        insertTaxonomy(FAR_IN_RADIUS_NPI, "207RC0000X", true);

        insertProvider(TOO_FAR_NPI, "TooFar", "Away", "10001", "40.712800", "-74.006000");
        insertTaxonomy(TOO_FAR_NPI, "207RC0000X", true);

        insertProvider(WRONG_SPECIALTY_NPI, "Wrong", "Specialty", "90802", "33.770000", "-118.191000");
        insertTaxonomy(WRONG_SPECIALTY_NPI, "207N00000X", true);

        ProviderSearchResponseDto response = providerSearchService.search("CARDIOLOGY", "90802", 25, 0, 20);

        Map<String, ProviderSearchResultDto> byNpi =
                response.results().stream().collect(toMap(ProviderSearchResultDto::npiNumber, r -> r));

        assertThat(byNpi).containsKeys(NEAR_NPI, FAR_IN_RADIUS_NPI);
        assertThat(byNpi).doesNotContainKeys(TOO_FAR_NPI, WRONG_SPECIALTY_NPI);
        assertThat(byNpi.get(NEAR_NPI).distanceMiles()).isLessThan(byNpi.get(FAR_IN_RADIUS_NPI).distanceMiles());
        assertThat(byNpi.get(NEAR_NPI).taxonomyCode()).isEqualTo("207RC0000X");

        for (int i = 0; i < response.results().size() - 1; i++) {
            assertThat(response.results().get(i).distanceMiles())
                    .isLessThanOrEqualTo(response.results().get(i + 1).distanceMiles());
        }
    }

    private void insertProvider(String npi, String firstName, String lastName, String zip, String lat, String lon) {
        jdbcTemplate.update(
                "INSERT INTO provider (npi_number, first_name, last_name, address_line_1, city, state_code, "
                        + "postal_code, latitude, longitude) VALUES (?, ?, ?, '1 Test St', 'Test City', 'CA', ?, ?, ?)",
                npi,
                firstName,
                lastName,
                zip,
                new java.math.BigDecimal(lat),
                new java.math.BigDecimal(lon));
    }

    private void insertTaxonomy(String npi, String taxonomyCode, boolean primary) {
        Long providerId =
                jdbcTemplate.queryForObject("SELECT id FROM provider WHERE npi_number = ?", Long.class, npi);
        jdbcTemplate.update(
                "INSERT INTO provider_taxonomy (provider_id, taxonomy_code, primary_taxonomy) VALUES (?, ?, ?)",
                providerId,
                taxonomyCode,
                primary);
    }
}
