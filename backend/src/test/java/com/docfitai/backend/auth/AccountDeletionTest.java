package com.docfitai.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.docfitai.backend.account.SavedProviderRepository;
import com.docfitai.backend.account.SavedSearchRepository;
import com.docfitai.backend.account.ShortlistRepository;
import com.docfitai.backend.auth.dto.AuthResponseDto;
import com.docfitai.backend.navigator.ProviderNavigationRepository;
import com.docfitai.backend.navigator.ProviderVerificationItemRepository;
import com.docfitai.backend.navigator.UserReminderRepository;
import com.docfitai.backend.navigator.UserSavedPlanRepository;
import com.docfitai.backend.testsupport.PostgresIntegrationSupport;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * CLAUDE.md "Account Deletion Test": create a user with every kind of user-owned data (saved
 * provider, saved search, shortlist, saved plan, navigation status, verification item, reminder),
 * delete the account, and verify every user-owned row is gone while the public provider record
 * (and any other user's data) is untouched.
 */
@AutoConfigureMockMvc
class AccountDeletionTest extends PostgresIntegrationSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SavedProviderRepository savedProviderRepository;

    @Autowired
    private SavedSearchRepository savedSearchRepository;

    @Autowired
    private ShortlistRepository shortlistRepository;

    @Autowired
    private ProviderNavigationRepository providerNavigationRepository;

    @Autowired
    private ProviderVerificationItemRepository providerVerificationItemRepository;

    @Autowired
    private UserReminderRepository userReminderRepository;

    @Autowired
    private UserSavedPlanRepository userSavedPlanRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deletingAnAccountRemovesEveryUserOwnedRowButLeavesPublicDataAndOtherUsersIntact() throws Exception {
        Long providerId = insertProviderWithLocation(
                jdbcTemplate, "9400000001", "Deletion", "Test", "1 Test St", "Long Beach", "CA", "90802", null, 33.77, -118.19);
        Long planId = insertInsurancePlan(jdbcTemplate, "DELETION_TEST_PAYER", "Deletion Test Payer", "Deletion Test Plan");

        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "delete-cascade@example.com", "password", "TestPassword123"))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        AuthResponseDto auth = objectMapper.readValue(response, AuthResponseDto.class);
        String token = auth.accessToken();
        Long userId = auth.user().id();

        // A second, untouched user -- proves deletion is scoped, not global.
        String otherResponse = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "delete-cascade-other@example.com", "password", "TestPassword123"))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long otherUserId = objectMapper.readValue(otherResponse, AuthResponseDto.class).user().id();
        savedProviderRepository.save(new com.docfitai.backend.account.SavedProvider(otherUserId, providerId, Instant.now()));

        mockMvc.perform(post("/api/saved-providers/" + providerId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/saved-searches")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("specialtyCode", "CARDIOLOGY", "locationText", "Long Beach, CA", "radius", 25, "sort", "distance"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/account/shortlists")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Deletion test shortlist"))))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/api/account/providers/" + providerId + "/navigation-status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "TO_CONTACT"))))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/account/providers/" + providerId + "/verification-items/PHONE")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "CONFIRMED_BY_USER"))))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/account/saved-plan")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("insurancePlanId", planId))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/account/reminders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("title", "Follow up", "dueAt", Instant.now().plus(Duration.ofDays(1))))))
                .andExpect(status().isCreated());

        assertThat(savedProviderRepository.findByUserIdOrderByCreatedAtDesc(userId)).isNotEmpty();
        assertThat(shortlistRepository.findByUserIdOrderByCreatedAtDesc(userId)).isNotEmpty();
        assertThat(providerNavigationRepository.findByUserId(userId)).isNotEmpty();
        assertThat(providerVerificationItemRepository.findByUserId(userId)).isNotEmpty();
        assertThat(userSavedPlanRepository.findByUserId(userId)).isPresent();

        mockMvc.perform(delete("/api/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());

        assertThat(savedProviderRepository.findByUserIdOrderByCreatedAtDesc(userId)).isEmpty();
        assertThat(savedSearchRepository.findByUserIdOrderByCreatedAtDesc(userId)).isEmpty();
        assertThat(shortlistRepository.findByUserIdOrderByCreatedAtDesc(userId)).isEmpty();
        assertThat(providerNavigationRepository.findByUserId(userId)).isEmpty();
        assertThat(providerVerificationItemRepository.findByUserId(userId)).isEmpty();
        assertThat(userReminderRepository.findByIdAndUserId(1L, userId)).isEmpty();
        assertThat(userSavedPlanRepository.findByUserId(userId)).isEmpty();

        // Public provider data is untouched.
        Long providerCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM provider WHERE id = ?", Long.class, providerId);
        assertThat(providerCount).isEqualTo(1L);

        // The other user's saved-provider row is untouched by the first user's deletion.
        assertThat(savedProviderRepository.findByUserIdOrderByCreatedAtDesc(otherUserId)).isNotEmpty();
    }
}
