package com.docfitai.backend.reference;

import static org.assertj.core.api.Assertions.assertThat;

import com.docfitai.backend.reference.dto.LocationSuggestionDto;
import com.docfitai.backend.testsupport.PostgresIntegrationSupport;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The shared test DB only carries the 6-row legacy Long Beach-area demo seed (V3 migration) --
 * the real 295-row LA County dataset is loaded by a separate, disabled-by-default runtime import
 * (CLAUDE.md "Geography Import Pipeline"), not by a Flyway migration. Tests that need a specific
 * city/ZIP shape insert their own fixture rows (unique "999xx" ZIPs, never colliding with the
 * demo seed or another test) rather than assuming production data is present.
 */
class LocationSuggestionServiceTest extends PostgresIntegrationSupport {

    @Autowired
    private LocationSuggestionService locationSuggestionService;

    @Autowired
    private ZipGeographyRepository zipGeographyRepository;

    @Test
    void matchesByCityNamePrefixCaseInsensitivelyAsOneDedupedCitySuggestion() {
        List<LocationSuggestionDto> results = locationSuggestionService.suggest("long");

        // Long Beach spans a dozen+ real LA County ZIPs -- CLAUDE.md "Location Suggestions V3"
        // requires exactly one suggestion for the city, not one per ZIP.
        assertThat(results).hasSize(1);
        LocationSuggestionDto suggestion = results.get(0);
        assertThat(suggestion.type()).isEqualTo(LocationSuggestionDto.TYPE_CITY);
        assertThat(suggestion.city()).isEqualTo("Long Beach");
        assertThat(suggestion.zipCode()).isNull();
    }

    @Test
    void matchesByZipPrefix() {
        List<LocationSuggestionDto> results = locationSuggestionService.suggest("908");

        assertThat(results).isNotEmpty();
        assertThat(results).allSatisfy(r -> {
            assertThat(r.type()).isEqualTo(LocationSuggestionDto.TYPE_ZIP);
            assertThat(r.zipCode()).startsWith("908");
        });
    }

    @Test
    void exactZipMatchIsRankedFirst() {
        List<LocationSuggestionDto> results = locationSuggestionService.suggest("90802");

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).zipCode()).isEqualTo("90802");
        assertThat(results.get(0).type()).isEqualTo(LocationSuggestionDto.TYPE_ZIP);
    }

    @Test
    void exactCityPrefixIsRankedBeforeWeakerContainsMatch() {
        zipGeographyRepository.save(new ZipGeography(
                "99930", "Zestful Springs", "CA", new BigDecimal("34.0"), new BigDecimal("-118.0")));
        zipGeographyRepository.save(new ZipGeography(
                "99931", "Boca Zestosa", "CA", new BigDecimal("34.0"), new BigDecimal("-118.0")));

        // "Zestful Springs" starts with "zest" (tier 1); "Boca Zestosa" only contains "zest"
        // (tier 3, the weaker match) -- the prefix match must be ranked first.
        List<LocationSuggestionDto> results = locationSuggestionService.suggest("zest");

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).city()).isEqualTo("Zestful Springs");
    }

    @Test
    void resultsAreBounded() {
        List<LocationSuggestionDto> results = locationSuggestionService.suggest("9");
        assertThat(results.size()).isLessThanOrEqualTo(8);
    }

    @Test
    void blankQueryReturnsBoundedDedupedCitySet() {
        List<LocationSuggestionDto> results = locationSuggestionService.suggest("");

        assertThat(results).isNotEmpty();
        assertThat(results.size()).isLessThanOrEqualTo(8);
        assertThat(results).allSatisfy(r -> assertThat(r.type()).isEqualTo(LocationSuggestionDto.TYPE_CITY));
        // No duplicate cities even with 295 real ZIPs loaded.
        assertThat(results.stream().map(LocationSuggestionDto::city).distinct().count()).isEqualTo(results.size());
    }

    @Test
    void unknownQueryReturnsEmptyRatherThanError() {
        assertThat(locationSuggestionService.suggest("Nowhereville")).isEmpty();
    }

    @Test
    void zipLevelZipCodeMatchingRowWithNoResolvedCityStillSuggestsHonestly() {
        // A real, legitimate LA County ZCTA can have no resolvable primary city (CLAUDE.md "City
        // Representation Limitations") -- the ZIP suggestion must not fabricate one.
        zipGeographyRepository.save(
                new ZipGeography("99940", null, "CA", new BigDecimal("34.4"), new BigDecimal("-117.9")));

        List<LocationSuggestionDto> results = locationSuggestionService.suggest("99940");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).city()).isNull();
        assertThat(results.get(0).label()).doesNotContain("null");
    }
}
