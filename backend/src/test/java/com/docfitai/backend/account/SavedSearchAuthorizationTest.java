package com.docfitai.backend.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.docfitai.backend.account.dto.SavedSearchDto;
import com.docfitai.backend.testsupport.AuthTestHelper;
import com.docfitai.backend.testsupport.PostgresIntegrationSupport;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

/** Critical: a saved search is only ever created by an explicit user action, and one user must never read/delete another's. */
@AutoConfigureMockMvc
class SavedSearchAuthorizationTest extends PostgresIntegrationSupport {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Map<String, Object> sampleSearchPayload() {
        return Map.of(
                "specialtyCode", "CARDIOLOGY",
                "locationText", "90802",
                "radius", 25,
                "sort", "distance");
    }

    @Test
    void anonymousRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/saved-searches")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/saved-searches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleSearchPayload())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/saved-searches/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void userCannotReadOrDeleteAnotherUsersSavedSearch() throws Exception {
        String ownerToken = AuthTestHelper.registerAndGetAccessToken(
                mockMvc, objectMapper, "search-victim@example.com", "TestPassword123");

        MvcResult saveResult = mockMvc.perform(post("/api/saved-searches")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleSearchPayload())))
                .andExpect(status().isCreated())
                .andReturn();
        SavedSearchDto saved = objectMapper.readValue(saveResult.getResponse().getContentAsString(), SavedSearchDto.class);

        String attackerToken = AuthTestHelper.registerAndGetAccessToken(
                mockMvc, objectMapper, "search-attacker@example.com", "TestPassword123");

        // Attacker's own list must not contain the victim's saved search.
        String attackerList = mockMvc.perform(get("/api/saved-searches").header("Authorization", "Bearer " + attackerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(attackerList).doesNotContain("\"id\":" + saved.id());

        // Attacker cannot delete the victim's saved search by guessing its id.
        mockMvc.perform(delete("/api/saved-searches/" + saved.id()).header("Authorization", "Bearer " + attackerToken))
                .andExpect(status().isNotFound());

        // It still exists for the real owner.
        String ownerList = mockMvc.perform(get("/api/saved-searches").header("Authorization", "Bearer " + ownerToken))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(ownerList).contains("\"id\":" + saved.id());
    }
}
