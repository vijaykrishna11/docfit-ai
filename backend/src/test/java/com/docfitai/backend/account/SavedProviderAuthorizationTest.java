package com.docfitai.backend.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.docfitai.backend.testsupport.AuthTestHelper;
import com.docfitai.backend.testsupport.PostgresIntegrationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * Critical: verifies anonymous users cannot access saved-provider data, and one authenticated
 * user cannot read/modify another user's saved providers (IDOR protection). The user id is
 * always derived from the validated access token -- never from a client-supplied value.
 */
@AutoConfigureMockMvc
class SavedProviderAuthorizationTest extends PostgresIntegrationSupport {

    private static final String NPI_A = "4000000001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Long insertProvider(String npi) {
        jdbcTemplate.update(
                "INSERT INTO provider (npi_number, first_name, last_name, address_line_1, city, state_code, "
                        + "postal_code, latitude, longitude) "
                        + "VALUES (?, 'Saved', 'TestDoctor', '1 Test St', 'Long Beach', 'CA', '90802', 33.77, -118.19)",
                npi);
        return jdbcTemplate.queryForObject("SELECT id FROM provider WHERE npi_number = ?", Long.class, npi);
    }

    @Test
    void anonymousRequestsAreRejected() throws Exception {
        Long providerId = insertProvider(NPI_A);

        mockMvc.perform(get("/api/saved-providers")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/saved-providers/" + providerId)).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/saved-providers/" + providerId)).andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUserCanSaveListAndRemoveTheirOwnProvider() throws Exception {
        Long providerId = insertProvider("4000000002");
        String token =
                AuthTestHelper.registerAndGetAccessToken(mockMvc, objectMapper, "saved-owner@example.com", "TestPassword123");

        mockMvc.perform(post("/api/saved-providers/" + providerId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        String listBody = mockMvc.perform(get("/api/saved-providers").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(listBody).contains("\"providerId\":" + providerId);

        mockMvc.perform(delete("/api/saved-providers/" + providerId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        String afterRemoval = mockMvc.perform(get("/api/saved-providers").header("Authorization", "Bearer " + token))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(afterRemoval).doesNotContain("\"providerId\":" + providerId);
    }

    @Test
    void userCannotSeeOrDeleteAnotherUsersSavedProvider() throws Exception {
        Long providerId = insertProvider("4000000003");

        String ownerToken =
                AuthTestHelper.registerAndGetAccessToken(mockMvc, objectMapper, "saved-victim@example.com", "TestPassword123");
        mockMvc.perform(post("/api/saved-providers/" + providerId).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        String attackerToken = AuthTestHelper.registerAndGetAccessToken(
                mockMvc, objectMapper, "saved-attacker@example.com", "TestPassword123");

        // The attacker's own list must NOT contain the victim's saved provider.
        String attackerList = mockMvc.perform(get("/api/saved-providers").header("Authorization", "Bearer " + attackerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(attackerList).doesNotContain("\"providerId\":" + providerId);

        // The attacker "deleting" the same providerId only affects their own (nonexistent) row --
        // it must not remove the victim's saved provider.
        mockMvc.perform(delete("/api/saved-providers/" + providerId).header("Authorization", "Bearer " + attackerToken))
                .andExpect(status().isNoContent());

        String ownerListAfterAttack = mockMvc.perform(get("/api/saved-providers").header("Authorization", "Bearer " + ownerToken))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(ownerListAfterAttack).contains("\"providerId\":" + providerId);
    }
}
