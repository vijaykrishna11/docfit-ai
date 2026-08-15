package com.docfitai.backend.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.docfitai.backend.provider.dto.ProviderDetailDto;
import com.docfitai.backend.testsupport.PostgresIntegrationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
class ProviderDetailApiTest extends PostgresIntegrationSupport {

    private static final String NPI = "3000000001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void detailEndpointReturnsProviderWithTaxonomiesAndDistance() throws Exception {
        jdbcTemplate.update(
                "INSERT INTO provider (npi_number, first_name, last_name, address_line_1, city, state_code, "
                        + "postal_code, latitude, longitude) "
                        + "VALUES (?, 'Detail', 'TestDoctor', '300 Ocean Blvd', 'Long Beach', 'CA', '90802', 33.770000, -118.191000)",
                NPI);
        Long providerId = jdbcTemplate.queryForObject("SELECT id FROM provider WHERE npi_number = ?", Long.class, NPI);
        jdbcTemplate.update(
                "INSERT INTO provider_taxonomy (provider_id, taxonomy_code, primary_taxonomy) VALUES (?, '207RC0000X', true)",
                providerId);

        String responseBody = mockMvc.perform(get("/api/providers/" + providerId).param("zip", "90802"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        ProviderDetailDto detail = objectMapper.readValue(responseBody, ProviderDetailDto.class);

        assertThat(detail.npiNumber()).isEqualTo(NPI);
        assertThat(detail.taxonomies()).hasSize(1);
        assertThat(detail.taxonomies().get(0).taxonomyCode()).isEqualTo("207RC0000X");
        assertThat(detail.taxonomies().get(0).primaryTaxonomy()).isTrue();
        assertThat(detail.distanceMiles()).isEqualTo(0.0);
    }

    @Test
    void detailEndpointReturns404ForUnknownProvider() throws Exception {
        mockMvc.perform(get("/api/providers/999999999")).andExpect(status().isNotFound());
    }
}
