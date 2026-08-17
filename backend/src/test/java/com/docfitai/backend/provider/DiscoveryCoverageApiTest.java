package com.docfitai.backend.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.docfitai.backend.provider.dto.CoverageDto;
import com.docfitai.backend.testsupport.PostgresIntegrationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/** CLAUDE.md "Coverage API": public, real counts only, never a hardcoded/marketing number. */
@AutoConfigureMockMvc
class DiscoveryCoverageApiTest extends PostgresIntegrationSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void coverageIsPubliclyReadableAndReflectsRealCounts() throws Exception {
        Long providerCountBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM provider", Long.class);

        insertProviderWithLocation(
                jdbcTemplate, "8900000001", "Coverage", "Test", "1 Test St", "Long Beach", "CA", "90802", null, 33.77, -118.19);

        String response = mockMvc.perform(get("/api/discovery/coverage"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        CoverageDto coverage = objectMapper.readValue(response, CoverageDto.class);

        assertThat(coverage.providerCount()).isEqualTo(providerCountBefore + 1);
        assertThat(coverage.specialtyCount()).isPositive();
        assertThat(coverage.areas()).isNotEmpty();
    }
}
