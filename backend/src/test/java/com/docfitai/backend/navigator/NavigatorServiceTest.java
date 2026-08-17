package com.docfitai.backend.navigator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.docfitai.backend.account.AppUser;
import com.docfitai.backend.account.AppUserRepository;
import com.docfitai.backend.navigator.dto.NavigationStatusDto;
import com.docfitai.backend.navigator.dto.NavigatorDashboardDto;
import com.docfitai.backend.navigator.dto.VerificationItemDto;
import com.docfitai.backend.testsupport.PostgresIntegrationSupport;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class NavigatorServiceTest extends PostgresIntegrationSupport {

    @Autowired
    private NavigatorService navigatorService;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void settingAStatusAlsoSavesTheProviderAndPersistsAcrossReads() {
        Long userId = insertUser("nav-status@example.com");
        Long providerId = insertProviderWithLocation(
                jdbcTemplate, "9100000001", "Status", "Test", "1 Test St", "Long Beach", "CA", "90802", null, 33.77, -118.19);

        NavigationStatusDto updated = navigatorService.updateStatus(userId, providerId, NavigationStatus.TO_CONTACT);
        assertThat(updated.status()).isEqualTo(NavigationStatus.TO_CONTACT);

        NavigatorDashboardDto dashboard = navigatorService.getDashboard(userId);
        assertThat(dashboard.savedCount()).isEqualTo(1);
        assertThat(dashboard.toContactCount()).isEqualTo(1);
        assertThat(dashboard.providers()).hasSize(1);
        assertThat(dashboard.providers().get(0).status()).isEqualTo(NavigationStatus.TO_CONTACT);
        assertThat(dashboard.providers().get(0).nextAction()).isEqualTo("Contact office");
    }

    @Test
    void defaultStatusForASavedProviderWithNoExplicitStatusIsSaved() {
        Long userId = insertUser("nav-default-status@example.com");
        Long providerId = insertProviderWithLocation(
                jdbcTemplate, "9100000002", "Default", "Test", "1 Test St", "Long Beach", "CA", "90802", null, 33.77, -118.19);
        jdbcTemplate.update("INSERT INTO saved_provider (user_id, provider_id, created_at) VALUES (?, ?, now())", userId, providerId);

        NavigatorDashboardDto dashboard = navigatorService.getDashboard(userId);
        assertThat(dashboard.providers().get(0).status()).isEqualTo(NavigationStatus.SAVED);
        assertThat(dashboard.providers().get(0).nextAction()).isEqualTo("Review provider");
    }

    @Test
    void updateStatusRejectsAnUnknownProvider() {
        Long userId = insertUser("nav-unknown-provider@example.com");
        assertThatThrownBy(() -> navigatorService.updateStatus(userId, 999_999_999L, NavigationStatus.TO_CONTACT))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
                .isEqualTo(404);
    }

    @Test
    void verificationItemsDefaultToNotStartedThenPersistUpdatesAndFeedIntoCompletionCounts() {
        Long userId = insertUser("nav-verification@example.com");
        Long providerId = insertProviderWithLocation(
                jdbcTemplate, "9100000003", "Verify", "Test", "1 Test St", "Long Beach", "CA", "90802", null, 33.77, -118.19);
        navigatorService.updateStatus(userId, providerId, NavigationStatus.CONTACTED);

        List<VerificationItemDto> initial = navigatorService.getVerificationItems(userId, providerId);
        assertThat(initial).hasSize(VerificationType.values().length);
        assertThat(initial).allMatch(item -> item.status() == VerificationItemStatus.NOT_STARTED);

        navigatorService.updateVerificationItem(
                userId, providerId, VerificationType.INSURANCE_NETWORK, VerificationItemStatus.CONFIRMED_BY_USER, null);
        navigatorService.updateVerificationItem(
                userId, providerId, VerificationType.PHONE, VerificationItemStatus.NOT_APPLICABLE, null);

        VerificationItemDto insuranceItem = navigatorService.getVerificationItems(userId, providerId).stream()
                .filter(item -> item.verificationType() == VerificationType.INSURANCE_NETWORK)
                .findFirst()
                .orElseThrow();
        assertThat(insuranceItem.status()).isEqualTo(VerificationItemStatus.CONFIRMED_BY_USER);
        assertThat(insuranceItem.confirmedAt()).isNotNull();

        NavigatorDashboardDto dashboard = navigatorService.getDashboard(userId);
        assertThat(dashboard.providers().get(0).verificationCompleted()).isEqualTo(2);
        assertThat(dashboard.providers().get(0).verificationTotal()).isEqualTo(VerificationType.values().length);
        // Insurance is resolved but other items are not -- next action should ask for the rest, not re-ask about insurance.
        assertThat(dashboard.providers().get(0).nextAction()).isEqualTo("Confirm remaining details");
        assertThat(dashboard.verificationNeededCount()).isEqualTo(1);
    }

    @Test
    void markingEveryVerificationItemConfirmedMakesNextActionReviewYourShortlist() {
        Long userId = insertUser("nav-verification-complete@example.com");
        Long providerId = insertProviderWithLocation(
                jdbcTemplate, "9100000004", "Complete", "Test", "1 Test St", "Long Beach", "CA", "90802", null, 33.77, -118.19);
        navigatorService.updateStatus(userId, providerId, NavigationStatus.CONTACTED);
        for (VerificationType type : VerificationType.values()) {
            navigatorService.updateVerificationItem(userId, providerId, type, VerificationItemStatus.CONFIRMED_BY_USER, null);
        }

        NavigatorDashboardDto dashboard = navigatorService.getDashboard(userId);
        assertThat(dashboard.providers().get(0).nextAction()).isEqualTo("Review your shortlist");
        assertThat(dashboard.verificationNeededCount()).isZero();
    }

    @Test
    void dashboardAppliesNetworkEvidenceOnlyWhenAPlanIsSaved() {
        Long userId = insertUser("nav-evidence@example.com");
        Long providerId = insertProviderWithLocation(
                jdbcTemplate, "9100000005", "Evidence", "Test", "1 Test St", "Long Beach", "CA", "90802", null, 33.77, -118.19);
        navigatorService.updateStatus(userId, providerId, NavigationStatus.SAVED);

        NavigatorDashboardDto withoutPlan = navigatorService.getDashboard(userId);
        assertThat(withoutPlan.savedPlan()).isNull();
        assertThat(withoutPlan.providers().get(0).networkEvidence()).isNull();
    }

    private Long insertUser(String email) {
        AppUser user = appUserRepository.save(new AppUser(email, "irrelevant-hash", "Test User", Instant.now(), Instant.now()));
        return user.getId();
    }
}
