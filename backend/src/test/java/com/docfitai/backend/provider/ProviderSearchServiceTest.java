package com.docfitai.backend.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static java.util.stream.Collectors.toMap;

import com.docfitai.backend.provider.dto.ProviderSearchResponseDto;
import com.docfitai.backend.provider.dto.ProviderSearchResultDto;
import com.docfitai.backend.testsupport.PostgresIntegrationSupport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

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

        ProviderSearchResponseDto response = providerSearchService.search(
                new ProviderSearchQuery("CARDIOLOGY", "90802", null, null, null, 25, "distance", 0, 200, null));

        Map<String, ProviderSearchResultDto> byNpi =
                response.results().stream().collect(toMap(ProviderSearchResultDto::npiNumber, r -> r));

        assertThat(byNpi).containsKeys(NEAR_NPI, FAR_IN_RADIUS_NPI);
        assertThat(byNpi).doesNotContainKeys(TOO_FAR_NPI, WRONG_SPECIALTY_NPI);
        assertThat(byNpi.get(NEAR_NPI).distanceMiles()).isLessThan(byNpi.get(FAR_IN_RADIUS_NPI).distanceMiles());
        assertThat(byNpi.get(NEAR_NPI).taxonomyCode()).isEqualTo("207RC0000X");
        assertThat(response.originLabel()).isEqualTo("Long Beach, CA");

        for (int i = 0; i < response.results().size() - 1; i++) {
            assertThat(response.results().get(i).distanceMiles())
                    .isLessThanOrEqualTo(response.results().get(i + 1).distanceMiles());
        }
    }

    @Test
    void radiusIsActuallyApplied() {
        String near = "1000000005";
        String farButWithinDefault = "1000000006";

        insertProvider(near, "Near", "Doctor", "90802", "33.770000", "-118.191000");
        insertTaxonomy(near, "207RC0000X", true);

        insertProvider(farButWithinDefault, "FarButIn", "Radius", "90815", "33.794000", "-118.116000");
        insertTaxonomy(farButWithinDefault, "207RC0000X", true);

        ProviderSearchResponseDto narrow = providerSearchService.search(
                new ProviderSearchQuery("CARDIOLOGY", "90802", null, null, null, 2, "distance", 0, 20, null));

        assertThat(narrow.results()).extracting(ProviderSearchResultDto::npiNumber).contains(near);
        assertThat(narrow.results())
                .extracting(ProviderSearchResultDto::npiNumber)
                .doesNotContain(farButWithinDefault);
    }

    @Test
    void searchSupportsLatLngAndFreeTextCityOrigin() {
        String near = "1000000007";
        insertProvider(near, "Near", "Doctor", "90802", "33.770000", "-118.191000");
        insertTaxonomy(near, "207RC0000X", true);

        // size=200: this suite shares one database across many test classes with overlapping
        // 90802/CARDIOLOGY fixtures, so a small page size can truncate before reaching this
        // test's own provider -- bumped like the sort tests below already do.
        ProviderSearchResponseDto byLatLng = providerSearchService.search(
                new ProviderSearchQuery("CARDIOLOGY", null, null, 33.770000, -118.191000, 25, "distance", 0, 200, null));
        assertThat(byLatLng.results()).extracting(ProviderSearchResultDto::npiNumber).contains(near);
        assertThat(byLatLng.originLabel()).isNull();

        ProviderSearchResponseDto byCity = providerSearchService.search(
                new ProviderSearchQuery("CARDIOLOGY", null, "Long Beach", null, null, 25, "distance", 0, 200, null));
        assertThat(byCity.results()).extracting(ProviderSearchResultDto::npiNumber).contains(near);
        assertThat(byCity.originLabel()).isEqualTo("Long Beach, CA");
    }

    @Test
    void nameSortOrdersAlphabeticallyRegardlessOfDistance() {
        String zedFar = "1000000008";
        String amyNear = "1000000009";

        insertProvider(zedFar, "Zed", "Provider", "90815", "33.794000", "-118.116000");
        insertTaxonomy(zedFar, "207RC0000X", true);

        insertProvider(amyNear, "Amy", "Provider", "90802", "33.770000", "-118.191000");
        insertTaxonomy(amyNear, "207RC0000X", true);

        ProviderSearchResponseDto response = providerSearchService.search(
                new ProviderSearchQuery("CARDIOLOGY", "90802", null, null, null, 25, "name", 0, 200, null));

        List<String> npis =
                response.results().stream().map(ProviderSearchResultDto::npiNumber).toList();
        assertThat(npis).contains(amyNear, zedFar);
        assertThat(npis.indexOf(amyNear)).isLessThan(npis.indexOf(zedFar));
    }

    @Test
    void nameDescSortReversesAlphabeticalOrder() {
        String zed = "1000000011";
        String amy = "1000000012";

        insertProvider(zed, "Zed", "Provider", "90802", "33.770000", "-118.191000");
        insertTaxonomy(zed, "207RC0000X", true);

        insertProvider(amy, "Amy", "Provider", "90802", "33.770000", "-118.191000");
        insertTaxonomy(amy, "207RC0000X", true);

        ProviderSearchResponseDto response = providerSearchService.search(
                new ProviderSearchQuery("CARDIOLOGY", "90802", null, null, null, 25, "name-desc", 0, 200, null));

        List<String> npis =
                response.results().stream().map(ProviderSearchResultDto::npiNumber).toList();
        assertThat(npis).contains(amy, zed);
        assertThat(npis.indexOf(zed)).isLessThan(npis.indexOf(amy));
    }

    @Test
    void fiftyMileRadiusIncludesProvidersOutsideDefaultRadius() {
        String midRange = "1000000010";
        // ~35 miles due north of 90802 -- outside the 25-mile default, inside 50.
        insertProvider(midRange, "MidRange", "Doctor", "90802", "34.277000", "-118.191000");
        insertTaxonomy(midRange, "207RC0000X", true);

        ProviderSearchResponseDto at25 = providerSearchService.search(
                new ProviderSearchQuery("CARDIOLOGY", "90802", null, null, null, 25, "distance", 0, 200, null));
        assertThat(at25.results()).extracting(ProviderSearchResultDto::npiNumber).doesNotContain(midRange);

        ProviderSearchResponseDto at50 = providerSearchService.search(
                new ProviderSearchQuery("CARDIOLOGY", "90802", null, null, null, 50, "distance", 0, 200, null));
        assertThat(at50.results()).extracting(ProviderSearchResultDto::npiNumber).contains(midRange);
    }

    @Test
    void oversizedRadiusAndPageSizeAreClampedRatherThanHonoredVerbatim() {
        String clampNpi = "1000000013";
        insertProvider(clampNpi, "Clamp", "Test", "90802", "33.770000", "-118.191000");
        insertTaxonomy(clampNpi, "207RC0000X", true);

        // A client asking for an enormous radius/page size must not force the server to load or
        // return an unbounded result set -- see MAX_RADIUS_MILES / MAX_PAGE_SIZE.
        ProviderSearchResponseDto response = providerSearchService.search(
                new ProviderSearchQuery("CARDIOLOGY", "90802", null, null, null, 999_999, "distance", 0, 999_999, null));

        assertThat(response.size()).isEqualTo(ProviderSearchService.MAX_PAGE_SIZE);
        assertThat(response.results()).extracting(ProviderSearchResultDto::npiNumber).contains(clampNpi);
    }

    @Test
    void unknownLocationIsRejectedWithBadRequest() {
        assertThatThrownBy(() -> providerSearchService.search(
                        new ProviderSearchQuery("CARDIOLOGY", null, "Nowhereville", null, null, 25, "distance", 0, 20, null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
                .isEqualTo(400);
    }

    private void insertProvider(String npi, String firstName, String lastName, String zip, String lat, String lon) {
        insertProviderWithLocation(
                jdbcTemplate, npi, firstName, lastName, "1 Test St", "Test City", "CA", zip, null,
                Double.valueOf(lat), Double.valueOf(lon));
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
