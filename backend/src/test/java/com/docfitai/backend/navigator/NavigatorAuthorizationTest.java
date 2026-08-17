package com.docfitai.backend.navigator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.docfitai.backend.navigator.dto.ReminderDto;
import com.docfitai.backend.testsupport.AuthTestHelper;
import com.docfitai.backend.testsupport.PostgresIntegrationSupport;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * Critical (CLAUDE.md "Status Authorization" / "Plan Authorization Tests" / "Authorization
 * Integration Tests"): User A must never read or modify User B's navigation status, verification
 * checklist, reminders, saved plan, or data export. Every endpoint resolves identity from the
 * access token only -- none accept a client-supplied user id.
 */
@AutoConfigureMockMvc
class NavigatorAuthorizationTest extends PostgresIntegrationSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void anonymousRequestsToEveryNavigatorEndpointAreRejected() throws Exception {
        mockMvc.perform(get("/api/account/navigator")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/account/providers/1/navigation-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "SAVED"))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/account/providers/1/verification-items")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/account/reminders")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/account/saved-plan")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/account/export")).andExpect(status().isUnauthorized());
    }

    @Test
    void navigationStatusAndVerificationItemsAreIsolatedPerUser() throws Exception {
        String ownerToken =
                AuthTestHelper.registerAndGetAccessToken(mockMvc, objectMapper, "navigator-victim@example.com", "TestPassword123");
        String attackerToken =
                AuthTestHelper.registerAndGetAccessToken(mockMvc, objectMapper, "navigator-attacker@example.com", "TestPassword123");
        Long providerId = insertProviderWithLocation(
                jdbcTemplate, "9300000001", "Nav", "Auth", "1 Test St", "Long Beach", "CA", "90802", null, 33.77, -118.19);

        mockMvc.perform(put("/api/account/providers/" + providerId + "/navigation-status")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "TO_CONTACT"))))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/account/providers/" + providerId + "/verification-items/INSURANCE_NETWORK")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "CONFIRMED_BY_USER"))))
                .andExpect(status().isOk());

        // The attacker's own dashboard must not show the victim's saved provider or status at all.
        String attackerDashboard = mockMvc.perform(
                        get("/api/account/navigator").header("Authorization", "Bearer " + attackerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(attackerDashboard).doesNotContain("TO_CONTACT");

        // The attacker checking the same provider's verification items sees their own (untouched) defaults, not the victim's confirmation.
        String attackerItems = mockMvc.perform(get("/api/account/providers/" + providerId + "/verification-items")
                        .header("Authorization", "Bearer " + attackerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(attackerItems).doesNotContain("CONFIRMED_BY_USER");

        // The victim's own state is untouched.
        String ownerDashboard = mockMvc.perform(
                        get("/api/account/navigator").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(ownerDashboard).contains("TO_CONTACT");
    }

    @Test
    void reminderOwnershipIsEnforced() throws Exception {
        String ownerToken =
                AuthTestHelper.registerAndGetAccessToken(mockMvc, objectMapper, "reminder-auth-victim@example.com", "TestPassword123");
        String attackerToken = AuthTestHelper.registerAndGetAccessToken(
                mockMvc, objectMapper, "reminder-auth-attacker@example.com", "TestPassword123");

        MvcResult createResult = mockMvc.perform(post("/api/account/reminders")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Follow up with provider", "dueAt", Instant.now().plus(Duration.ofDays(1))))))
                .andExpect(status().isCreated())
                .andReturn();
        ReminderDto reminder = objectMapper.readValue(createResult.getResponse().getContentAsString(), ReminderDto.class);

        mockMvc.perform(patch("/api/account/reminders/" + reminder.id())
                        .header("Authorization", "Bearer " + attackerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("completed", true))))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/account/reminders/" + reminder.id()).header("Authorization", "Bearer " + attackerToken))
                .andExpect(status().isNotFound());

        String attackerReminders = mockMvc.perform(
                        get("/api/account/reminders").header("Authorization", "Bearer " + attackerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(attackerReminders).doesNotContain("Follow up with provider");
    }

    @Test
    void savedPlanIsIsolatedPerUser() throws Exception {
        String ownerToken =
                AuthTestHelper.registerAndGetAccessToken(mockMvc, objectMapper, "plan-auth-victim@example.com", "TestPassword123");
        String attackerToken =
                AuthTestHelper.registerAndGetAccessToken(mockMvc, objectMapper, "plan-auth-attacker@example.com", "TestPassword123");
        Long planId = insertInsurancePlan(jdbcTemplate, "AUTH_TEST_PAYER", "Auth Test Payer", "Auth Test Plan");

        mockMvc.perform(put("/api/account/saved-plan")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("insurancePlanId", planId))))
                .andExpect(status().isOk());

        // Attacker has no saved plan of their own -- 204, never the victim's plan.
        mockMvc.perform(get("/api/account/saved-plan").header("Authorization", "Bearer " + attackerToken))
                .andExpect(status().isNoContent());

        String ownerPlan = mockMvc.perform(get("/api/account/saved-plan").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(ownerPlan).contains("Auth Test Plan");
    }

    @Test
    void dataExportOnlyEverContainsTheCallersOwnData() throws Exception {
        String ownerToken =
                AuthTestHelper.registerAndGetAccessToken(mockMvc, objectMapper, "export-auth-victim@example.com", "TestPassword123");
        String attackerToken =
                AuthTestHelper.registerAndGetAccessToken(mockMvc, objectMapper, "export-auth-attacker@example.com", "TestPassword123");
        Long providerId = insertProviderWithLocation(
                jdbcTemplate, "9300000002", "Export", "Auth", "1 Test St", "Long Beach", "CA", "90802", null, 33.77, -118.19);
        mockMvc.perform(put("/api/account/providers/" + providerId + "/navigation-status")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "TO_CONTACT"))))
                .andExpect(status().isOk());

        String attackerExport = mockMvc.perform(
                        get("/api/account/export").header("Authorization", "Bearer " + attackerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(attackerExport).doesNotContain("export-auth-victim@example.com");
        assertThat(attackerExport).contains("\"savedProviders\":[]").contains("\"navigation\":[]");

        String ownerExport = mockMvc.perform(get("/api/account/export").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(ownerExport).contains("export-auth-victim@example.com");
        assertThat(ownerExport).doesNotContain("passwordHash");
        assertThat(ownerExport).doesNotContain("refreshToken");
    }
}
