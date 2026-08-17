package com.docfitai.backend.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.docfitai.backend.account.dto.ShortlistDto;
import com.docfitai.backend.testsupport.AuthTestHelper;
import com.docfitai.backend.testsupport.PostgresIntegrationSupport;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

/** Critical: User A must never be able to read, rename, delete, or add/remove providers from User B's shortlist (CLAUDE.md "Shortlist Authorization"). */
@AutoConfigureMockMvc
class ShortlistAuthorizationTest extends PostgresIntegrationSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void anonymousRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/account/shortlists")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/account/shortlists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "My shortlist"))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/account/shortlists/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void userCannotReadRenameDeleteOrModifyAnotherUsersShortlist() throws Exception {
        String ownerToken =
                AuthTestHelper.registerAndGetAccessToken(mockMvc, objectMapper, "shortlist-victim@example.com", "TestPassword123");

        MvcResult createResult = mockMvc.perform(post("/api/account/shortlists")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Cardiology options"))))
                .andExpect(status().isCreated())
                .andReturn();
        ShortlistDto shortlist = objectMapper.readValue(createResult.getResponse().getContentAsString(), ShortlistDto.class);

        Long providerId = insertProviderWithLocation(
                jdbcTemplate, "8300000001", "IDOR", "Test", "1 Test St", "Long Beach", "CA", "90802", null, 33.77, -118.19);

        String attackerToken =
                AuthTestHelper.registerAndGetAccessToken(mockMvc, objectMapper, "shortlist-attacker@example.com", "TestPassword123");

        // Attacker's own list must not contain the victim's shortlist.
        String attackerList = mockMvc.perform(
                        get("/api/account/shortlists").header("Authorization", "Bearer " + attackerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(attackerList).doesNotContain("Cardiology options");

        // Reading the victim's shortlist directly by id is a 404 (not 403 -- existence isn't leaked).
        mockMvc.perform(get("/api/account/shortlists/" + shortlist.id()).header("Authorization", "Bearer " + attackerToken))
                .andExpect(status().isNotFound());

        // Renaming, deleting, and adding/removing a provider all 404 the same way.
        mockMvc.perform(patch("/api/account/shortlists/" + shortlist.id())
                        .header("Authorization", "Bearer " + attackerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Hijacked"))))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/account/shortlists/" + shortlist.id() + "/providers/" + providerId)
                        .header("Authorization", "Bearer " + attackerToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/account/shortlists/" + shortlist.id() + "/providers/" + providerId)
                        .header("Authorization", "Bearer " + attackerToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/account/shortlists/" + shortlist.id()).header("Authorization", "Bearer " + attackerToken))
                .andExpect(status().isNotFound());

        // The shortlist and its original name are untouched for the real owner.
        String ownerDetail = mockMvc.perform(
                        get("/api/account/shortlists/" + shortlist.id()).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(ownerDetail).contains("Cardiology options");
    }
}
