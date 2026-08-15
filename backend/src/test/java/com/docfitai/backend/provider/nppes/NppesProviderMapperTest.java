package com.docfitai.backend.provider.nppes;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class NppesProviderMapperTest {

    private static final Set<String> KNOWN_CODES = Set.of("207RC0000X", "207N00000X");

    @Test
    void parsesRawNppesJsonIntoRecords() {
        String json =
                """
                {
                  "result_count": 1,
                  "results": [
                    {
                      "number": "1234567890",
                      "basic": {"first_name": "Jane", "last_name": "Doe"},
                      "addresses": [
                        {"address_purpose": "LOCATION", "address_1": "100 Main St", "address_2": "Suite 200",
                         "city": "Long Beach", "state": "CA", "postal_code": "908021234", "telephone_number": "5625555678"}
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
        assertThat(response.results()).hasSize(1);
        NppesResult result = response.results().get(0);
        assertThat(result.number()).isEqualTo("1234567890");
        assertThat(result.basic().firstName()).isEqualTo("Jane");
        assertThat(result.addresses().get(0).addressPurpose()).isEqualTo("LOCATION");
        assertThat(result.taxonomies().get(0).code()).isEqualTo("207RC0000X");
    }

    @Test
    void mapsProviderWithMatchingPrimaryTaxonomyAndLocationAddress() {
        NppesResult result = new NppesResult(
                "1234567890",
                new NppesBasic("Jane", "Doe"),
                List.of(
                        new NppesAddress("MAILING", "PO Box 1", null, "Long Beach", "CA", "908021234", "5625551234"),
                        new NppesAddress(
                                "LOCATION", "100 Main St", "Suite 200", "Long Beach", "CA", "908021234", "5625555678")),
                List.of(
                        new NppesTaxonomy("207RC0000X", true, "Cardiovascular Disease"),
                        new NppesTaxonomy("101YM0800X", false, "Mental Health Counselor")));

        Optional<NppesProviderMapper.MappedProvider> mapped = NppesProviderMapper.map(result, KNOWN_CODES);

        assertThat(mapped).isPresent();
        NppesProviderMapper.MappedProvider provider = mapped.get();
        assertThat(provider.npiNumber()).isEqualTo("1234567890");
        assertThat(provider.firstName()).isEqualTo("Jane");
        assertThat(provider.lastName()).isEqualTo("Doe");
        assertThat(provider.addressLine1()).isEqualTo("100 Main St");
        assertThat(provider.postalCode()).isEqualTo("90802");
        assertThat(provider.phone()).isEqualTo("5625555678");
        assertThat(provider.matchingTaxonomies()).extracting(NppesTaxonomy::code).containsExactly("207RC0000X");
    }

    @Test
    void skipsProviderWithNoMatchingTaxonomy() {
        NppesResult result = new NppesResult(
                "9999999999",
                new NppesBasic("No", "Match"),
                List.of(new NppesAddress("LOCATION", "1 Elm St", null, "Long Beach", "CA", "90802", null)),
                List.of(new NppesTaxonomy("999X00000X", true, "Unrelated")));

        assertThat(NppesProviderMapper.map(result, KNOWN_CODES)).isEmpty();
    }

    @Test
    void skipsProviderWithNoLocationAddress() {
        NppesResult result = new NppesResult(
                "8888888888",
                new NppesBasic("No", "Location"),
                List.of(new NppesAddress("MAILING", "PO Box 2", null, "Long Beach", "CA", "90802", null)),
                List.of(new NppesTaxonomy("207RC0000X", true, "Cardiovascular Disease")));

        assertThat(NppesProviderMapper.map(result, KNOWN_CODES)).isEmpty();
    }
}
