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

        String responseBody = mockMvc.perform(
                        get("/api/providers/search").param("specialty", "CARDIOLOGY").param("zip", "90802"))
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
