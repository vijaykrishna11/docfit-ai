package com.docfitai.backend.provider.nppes;

import static org.assertj.core.api.Assertions.assertThat;

import com.docfitai.backend.provider.ProviderEntityType;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class NppesProviderMapperTest {

    private static final Set<String> KNOWN_CODES = Set.of("207RC0000X", "207N00000X");

    @Test
    void parsesRawNppesJsonIncludingPracticeLocationsAndEnumerationType() {
        String json =
                """
                {
                  "result_count": 1,
                  "results": [
                    {
                      "number": "1234567890",
                      "enumeration_type": "NPI-1",
                      "basic": {"first_name": "Jane", "last_name": "Doe"},
                      "addresses": [
                        {"address_purpose": "LOCATION", "address_1": "100 Main St", "address_2": "Suite 200",
                         "city": "Long Beach", "state": "CA", "postal_code": "908021234", "telephone_number": "5625555678"}
                      ],
                      "practiceLocations": [
                        {"address_purpose": "LOCATION", "address_1": "200 Second Office", "city": "Lakewood",
                         "state": "CA", "postal_code": "907120001", "telephone_number": "5625559999"}
                      ],
                      "taxonomies": [
                        {"code": "207RC0000X", "primary": true, "desc": "Cardiovascular Disease"}
                      ]
                    }
                  ]
                }
                """;

        NppesResponse response = new ObjectMapper().readValue(json, NppesResponse.class);

        assertThat(response.resultCount()).isEqualTo(1);
        NppesResult result = response.results().get(0);
        assertThat(result.number()).isEqualTo("1234567890");
        assertThat(result.enumerationType()).isEqualTo("NPI-1");
        assertThat(result.basic().firstName()).isEqualTo("Jane");
        assertThat(result.addresses().get(0).addressPurpose()).isEqualTo("LOCATION");
        assertThat(result.practiceLocations()).hasSize(1);
        assertThat(result.practiceLocations().get(0).city()).isEqualTo("Lakewood");
        assertThat(result.taxonomies().get(0).code()).isEqualTo("207RC0000X");
    }

    @Test
    void mapsIndividualProviderWithMatchingTaxonomyAndCombinesAddressesWithPracticeLocations() {
        NppesResult result = new NppesResult(
                "1234567890",
                "NPI-1",
                new NppesBasic("Jane", "Doe", null),
                List.of(
                        new NppesAddress("MAILING", "PO Box 1", null, "Long Beach", "CA", "908021234", "5625551234", null),
                        new NppesAddress("LOCATION", "100 Main St", "Suite 200", "Long Beach", "CA", "908021234", "5625555678", null)),
                List.of(new NppesAddress("LOCATION", "200 Second Office", null, "Lakewood", "CA", "907120001", "5625559999", null)),
                List.of(
                        new NppesTaxonomy("207RC0000X", true, "Cardiovascular Disease"),
                        new NppesTaxonomy("101YM0800X", false, "Mental Health Counselor")));

        Optional<NppesProviderMapper.MappedProvider> mapped = NppesProviderMapper.map(result, KNOWN_CODES);

        assertThat(mapped).isPresent();
        NppesProviderMapper.MappedProvider provider = mapped.get();
        assertThat(provider.identity().npiNumber()).isEqualTo("1234567890");
        assertThat(provider.identity().entityType()).isEqualTo(ProviderEntityType.INDIVIDUAL);
        assertThat(provider.identity().firstName()).isEqualTo("Jane");
        assertThat(provider.identity().lastName()).isEqualTo("Doe");
        // Both the single LOCATION address and the genuine NPPES practiceLocations entry are
        // captured -- real multi-location data, not a synthetic stand-in.
        assertThat(provider.locations()).hasSize(2);
        assertThat(provider.locations()).extracting(NppesProviderMapper.MappedLocation::city).containsExactlyInAnyOrder("Long Beach", "Lakewood");
        assertThat(provider.locations()).extracting(NppesProviderMapper.MappedLocation::postalCode).containsExactlyInAnyOrder("90802", "90712");
        assertThat(provider.taxonomies()).extracting(t -> t.taxonomyCode()).containsExactly("207RC0000X");
    }

    @Test
    void mapsOrganizationProviderUsingOrganizationName() {
        NppesResult result = new NppesResult(
                "1122334455",
                "NPI-2",
                new NppesBasic(null, null, "Long Beach Cardiology Medical Group"),
                List.of(new NppesAddress("LOCATION", "1 Group Way", null, "Long Beach", "CA", "90802", "5625550000", null)),
                List.of(),
                List.of(new NppesTaxonomy("207RC0000X", true, "Cardiovascular Disease")));

        Optional<NppesProviderMapper.MappedProvider> mapped = NppesProviderMapper.map(result, KNOWN_CODES);

        assertThat(mapped).isPresent();
        NppesProviderMapper.MappedProvider provider = mapped.get();
        assertThat(provider.identity().entityType()).isEqualTo(ProviderEntityType.ORGANIZATION);
        assertThat(provider.identity().organizationName()).isEqualTo("Long Beach Cardiology Medical Group");
        assertThat(provider.identity().firstName()).isNull();
    }

    @Test
    void deduplicatesExactDuplicateAddressAcrossAddressesAndPracticeLocations() {
        NppesAddress sameOffice = new NppesAddress("LOCATION", "100 Main St", null, "Long Beach", "CA", "90802", "5625555678", null);
        NppesResult result = new NppesResult(
                "1234567891",
                "NPI-1",
                new NppesBasic("Jane", "Doe", null),
                List.of(sameOffice),
                List.of(sameOffice),
                List.of(new NppesTaxonomy("207RC0000X", true, "Cardiovascular Disease")));

        Optional<NppesProviderMapper.MappedProvider> mapped = NppesProviderMapper.map(result, KNOWN_CODES);

        assertThat(mapped).isPresent();
        assertThat(mapped.get().locations()).hasSize(1);
    }

    @Test
    void skipsProviderWithNoMatchingTaxonomy() {
        NppesResult result = new NppesResult(
                "9999999999",
                "NPI-1",
                new NppesBasic("No", "Match", null),
                List.of(new NppesAddress("LOCATION", "1 Elm St", null, "Long Beach", "CA", "90802", null, null)),
                List.of(),
                List.of(new NppesTaxonomy("999X00000X", true, "Unrelated")));

        assertThat(NppesProviderMapper.map(result, KNOWN_CODES)).isEmpty();
    }

    @Test
    void skipsProviderWithNoLocationAddress() {
        NppesResult result = new NppesResult(
                "8888888888",
                "NPI-1",
                new NppesBasic("No", "Location", null),
                List.of(new NppesAddress("MAILING", "PO Box 2", null, "Long Beach", "CA", "90802", null, null)),
                List.of(),
                List.of(new NppesTaxonomy("207RC0000X", true, "Cardiovascular Disease")));

        assertThat(NppesProviderMapper.map(result, KNOWN_CODES)).isEmpty();
    }
}
