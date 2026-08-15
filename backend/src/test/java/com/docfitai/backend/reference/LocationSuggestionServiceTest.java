package com.docfitai.backend.reference;

import static org.assertj.core.api.Assertions.assertThat;

import com.docfitai.backend.reference.dto.LocationSuggestionDto;
import com.docfitai.backend.testsupport.PostgresIntegrationSupport;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class LocationSuggestionServiceTest extends PostgresIntegrationSupport {

    @Autowired
    private LocationSuggestionService locationSuggestionService;

    @Test
    void matchesByCityNameCaseInsensitively() {
        List<LocationSuggestionDto> results = locationSuggestionService.suggest("long");

        assertThat(results).isNotEmpty();
        assertThat(results).allSatisfy(r -> assertThat(r.city()).containsIgnoringCase("long"));
        assertThat(results).extracting(LocationSuggestionDto::zipCode).contains("90802");
    }

    @Test
    void matchesByZipPrefix() {
        List<LocationSuggestionDto> results = locationSuggestionService.suggest("908");

        assertThat(results).extracting(LocationSuggestionDto::zipCode).allMatch(zip -> zip.startsWith("908"));
    }

    @Test
    void blankQueryReturnsBoundedResultSet() {
        List<LocationSuggestionDto> results = locationSuggestionService.suggest("");

        assertThat(results).isNotEmpty();
        assertThat(results.size()).isLessThanOrEqualTo(8);
    }

    @Test
    void unknownQueryReturnsEmptyRatherThanError() {
        assertThat(locationSuggestionService.suggest("Nowhereville")).isEmpty();
    }
}
