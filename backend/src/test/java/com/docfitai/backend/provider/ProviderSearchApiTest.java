package com.docfitai.backend.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.docfitai.backend.provider.dto.ProviderSearchResponseDto;
import com.docfitai.backend.provider.dto.ProviderSearchResultDto;
import com.docfitai.backend.testsupport.PostgresIntegrationSupport;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
class ProviderSearchApiTest extends PostgresIntegrationSupport {

    private static final String NPI = "2000000001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void searchEndpointReturnsMatchingProvider() throws Exception {
        Long providerId = insertProviderWithLocation(
                jdbcTemplate, NPI, "Api", "TestDoctor", "200 Ocean Blvd", "Long Beach", "CA", "90802", null, 33.770000, -118.191000);
        jdbcTemplate.update(
                "INSERT INTO provider_taxonomy (provider_id, taxonomy_code, primary_taxonomy) VALUES (?, '207RC0000X', true)",
                providerId);

        // size=200: the shared, non-rolled-back test database accumulates other CARDIOLOGY-taxonomy
        // fixtures near this same Long Beach location across the rest of the suite (several other
        // test classes reuse taxonomy 207RC0000X at nearby coordinates), and the search's default
        // sort (nearest-first, MATCH_QUERY has no ORDER BY) has no secondary tie-breaker for
        // distance-tied results -- ties are broken by Postgres's own unordered row-return order,
        // which is not guaranteed. Without an explicit size, this provider can be pushed off the
        // default 20-result page depending on how many other same-distance fixtures have
        // accumulated by the time this test runs (same class of issue already fixed once in
        // ProviderSearchServiceTest.radiusIsActuallyApplied). A large explicit size keeps this test
        // deterministic without weakening what it proves: a known provider is genuinely returned.
        String responseBody = mockMvc.perform(
                        get("/api/providers/search")
                                .param("specialty", "CARDIOLOGY")
                                .param("zip", "90802")
                                .param("size", "200"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        ProviderSearchResponseDto response = objectMapper.readValue(responseBody, ProviderSearchResponseDto.class);

        Optional<ProviderSearchResultDto> match =
                response.results().stream().filter(r -> NPI.equals(r.npiNumber())).findFirst();

        assertThat(match).isPresent();
        assertThat(match.get().taxonomyCode()).isEqualTo("207RC0000X");
        assertThat(match.get().lastName()).isEqualTo("TestDoctor");
    }
}
