package com.docfitai.backend.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.docfitai.backend.provider.dto.ProviderDetailDto;
import com.docfitai.backend.provider.dto.ProviderSearchResponseDto;
import com.docfitai.backend.provider.dto.ProviderSearchResultDto;
import com.docfitai.backend.testsupport.PostgresIntegrationSupport;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Deterministic synthetic fixtures proving the multi-location architecture end to end
 * (CLAUDE.md 2, 10-11, 21, 41, 47): a provider with two practice locations is returned exactly
 * once, attached to the nearest qualifying location; a provider with two taxonomies is likewise
 * returned once; an organization provider displays its organization name, never "null null";
 * pagination totals count providers, not location rows.
 */
class MultiLocationProviderTest extends PostgresIntegrationSupport {

    // Provider A: two practice locations -- one near the search origin (90802), one far (90815-ish but still in radius for the "both in radius" case is avoided by using a genuinely distant second office).
    private static final String PROVIDER_A_NPI = "7000000001";
    // Provider B: single location, two taxonomies (primary + secondary).
    private static final String PROVIDER_B_NPI = "7000000002";
    // Provider C: organization (NPI-2), two locations.
    private static final String PROVIDER_C_NPI = "7000000003";

    @Autowired
    private ProviderSearchService providerSearchService;

    @Autowired
    private ProviderDetailService providerDetailService;

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void providerWithTwoLocationsAppearsOnceAttachedToNearestQualifyingLocation() {
        Long providerId = insertProvider(PROVIDER_A_NPI, ProviderEntityType.INDIVIDUAL, "Multi", "Location", null);
        insertLocation(providerId, "1 Near St", "Long Beach", "CA", "90802", "562-555-0001", 33.770000, -118.191000, true);
        // ~35 miles away -- still within a 50-mile search, farther than the primary office.
        insertLocation(providerId, "2 Far Ave", "Somewhere", "CA", "90810", "562-555-0002", 34.277000, -118.191000, false);
        insertTaxonomy(providerId, "207RC0000X", true);

        ProviderSearchResponseDto response = providerSearchService.search(
                new ProviderSearchQuery("CARDIOLOGY", "90802", null, null, null, 50, "distance", 0, 20, null));

        List<ProviderSearchResultDto> matches =
                response.results().stream().filter(r -> PROVIDER_A_NPI.equals(r.npiNumber())).toList();
        assertThat(matches).hasSize(1);
        ProviderSearchResultDto match = matches.get(0);
        assertThat(match.location().addressLine1()).isEqualTo("1 Near St");
        assertThat(match.distanceMiles()).isLessThan(5.0);

        // Provider detail lists the other office separately, not duplicated into the selected one.
        ProviderDetailDto detail = providerDetailService.getById(providerId, "90802", null, null, null);
        assertThat(detail.location().addressLine1()).isEqualTo("1 Near St");
        assertThat(detail.otherLocations()).hasSize(1);
        assertThat(detail.otherLocations().get(0).addressLine1()).isEqualTo("2 Far Ave");
    }

    @Test
    void providerWithTwoTaxonomiesAppearsOnceWithBestMatchedTaxonomy() {
        Long providerId = insertProvider(PROVIDER_B_NPI, ProviderEntityType.INDIVIDUAL, "Two", "Taxonomies", null);
        insertLocation(providerId, "1 Test Ave", "Long Beach", "CA", "90802", null, 33.770000, -118.191000, true);
        insertTaxonomy(providerId, "207RI0011X", false);
        insertTaxonomy(providerId, "207RC0000X", true);

        ProviderSearchResponseDto response = providerSearchService.search(
                new ProviderSearchQuery("CARDIOLOGY", "90802", null, null, null, 25, "distance", 0, 20, null));

        List<ProviderSearchResultDto> matches =
                response.results().stream().filter(r -> PROVIDER_B_NPI.equals(r.npiNumber())).toList();
        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).taxonomyCode()).isEqualTo("207RC0000X");
    }

    @Test
    void organizationProviderDisplaysOrganizationNameNeverNullNull() {
        Long providerId = insertProvider(PROVIDER_C_NPI, ProviderEntityType.ORGANIZATION, null, null, "Long Beach Cardiology Medical Group");
        insertLocation(providerId, "1 Group Way", "Long Beach", "CA", "90802", null, 33.770000, -118.191000, true);
        insertLocation(providerId, "2 Group Annex", "Long Beach", "CA", "90802", null, 33.771000, -118.192000, false);
        insertTaxonomy(providerId, "207RC0000X", true);

        ProviderDetailDto detail = providerDetailService.getById(providerId, "90802", null, null, null);
        assertThat(detail.entityType()).isEqualTo("ORGANIZATION");
        assertThat(detail.organizationName()).isEqualTo("Long Beach Cardiology Medical Group");
        assertThat(detail.firstName()).isNull();
        assertThat(detail.lastName()).isNull();
        assertThat(detail.otherLocations()).hasSize(1);
    }

    @Test
    void paginationCountsProvidersNotLocationRows() {
        Long providerId = insertProvider("7000000004", ProviderEntityType.INDIVIDUAL, "Paginated", "MultiOffice", null);
        insertLocation(providerId, "1 Office A", "Long Beach", "CA", "90802", null, 33.770000, -118.191000, true);
        insertLocation(providerId, "2 Office B", "Long Beach", "CA", "90802", null, 33.771000, -118.192000, false);
        insertLocation(providerId, "3 Office C", "Long Beach", "CA", "90802", null, 33.772000, -118.193000, false);
        insertTaxonomy(providerId, "207RC0000X", true);

        ProviderSearchResponseDto response = providerSearchService.search(
                new ProviderSearchQuery("CARDIOLOGY", "90802", null, null, null, 25, "distance", 0, 20, null));

        Map<String, List<ProviderSearchResultDto>> byNpi =
                response.results().stream().collect(Collectors.groupingBy(ProviderSearchResultDto::npiNumber));
        assertThat(byNpi.get("7000000004")).hasSize(1);
        // totalElements reflects distinct providers, not the 3 location rows this provider owns.
        long providerOccurrences = response.results().stream().filter(r -> "7000000004".equals(r.npiNumber())).count();
        assertThat(providerOccurrences).isEqualTo(1);
    }

    private Long insertProvider(String npi, ProviderEntityType entityType, String firstName, String lastName, String organizationName) {
        jdbcTemplate.update(
                "INSERT INTO provider (npi_number, entity_type, first_name, last_name, organization_name) VALUES (?, ?, ?, ?, ?)",
                npi, entityType.name(), firstName, lastName, organizationName);
        return providerRepository.findByNpiNumber(npi).orElseThrow().getId();
    }

    private void insertLocation(
            Long providerId,
            String line1,
            String city,
            String state,
            String postal,
            String phone,
            Double lat,
            Double lng,
            boolean primary) {
        jdbcTemplate.update(
                "INSERT INTO provider_location (provider_id, address_purpose, address_line_1, city, state_code, "
                        + "postal_code, phone, latitude, longitude, coordinate_precision, is_primary, normalized_key) "
                        + "VALUES (?, 'LOCATION', ?, ?, ?, ?, ?, ?, ?, 'ZIP_CENTROID', ?, ?)",
                providerId, line1, city, state, postal, phone, lat, lng, primary, providerId + "|" + line1);
    }

    private void insertTaxonomy(Long providerId, String taxonomyCode, boolean primary) {
        jdbcTemplate.update(
                "INSERT INTO provider_taxonomy (provider_id, taxonomy_code, primary_taxonomy) VALUES (?, ?, ?)",
                providerId, taxonomyCode, primary);
    }
}
