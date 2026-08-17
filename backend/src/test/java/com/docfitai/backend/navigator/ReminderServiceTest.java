package com.docfitai.backend.navigator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.docfitai.backend.account.AppUser;
import com.docfitai.backend.account.AppUserRepository;
import com.docfitai.backend.account.ShortlistService;
import com.docfitai.backend.account.dto.ShortlistDto;
import com.docfitai.backend.navigator.dto.ReminderDto;
import com.docfitai.backend.testsupport.PostgresIntegrationSupport;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class ReminderServiceTest extends PostgresIntegrationSupport {

    @Autowired
    private ReminderService reminderService;

    @Autowired
    private ShortlistService shortlistService;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createListCompleteAndDeleteWork() {
        Long userId = insertUser("reminder-basic@example.com");
        Long providerId = insertProviderWithLocation(
                jdbcTemplate, "9500000001", "Reminder", "Test", "1 Test St", "Long Beach", "CA", "90802", null, 33.77, -118.19);

        ReminderDto created =
                reminderService.create(userId, "Follow up with provider", Instant.now().plus(Duration.ofDays(1)), providerId, null);
        assertThat(created.providerName()).isEqualTo("Reminder Test");
        assertThat(created.completedAt()).isNull();

        assertThat(reminderService.upcoming(userId, 10)).extracting(ReminderDto::id).containsExactly(created.id());

        reminderService.setCompleted(userId, created.id(), true);
        assertThat(reminderService.list(userId).get(0).completedAt()).isNotNull();
        assertThat(reminderService.upcoming(userId, 10)).isEmpty();

        reminderService.setCompleted(userId, created.id(), false);
        assertThat(reminderService.list(userId).get(0).completedAt()).isNull();

        reminderService.delete(userId, created.id());
        assertThat(reminderService.list(userId)).isEmpty();
    }

    @Test
    void aPastDueDateIsAcceptedAndSimplyRendersAsAlreadyDue() {
        Long userId = insertUser("reminder-past@example.com");
        ReminderDto created = reminderService.create(userId, "Check this shortlist", Instant.now().minus(Duration.ofDays(2)), null, null);
        assertThat(created.dueAt()).isBefore(Instant.now());
    }

    @Test
    void anAbsurdlyFarFutureDateIsRejected() {
        Long userId = insertUser("reminder-absurd@example.com");
        assertThatThrownBy(() -> reminderService.create(
                        userId, "Follow up", Instant.now().plus(Duration.ofDays(365L * 20)), null, null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
                .isEqualTo(400);
    }

    @Test
    void creatingAReminderForAnUnknownProviderIs404() {
        Long userId = insertUser("reminder-unknown-provider@example.com");
        assertThatThrownBy(() -> reminderService.create(userId, "Follow up", Instant.now().plus(Duration.ofDays(1)), 999_999_999L, null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
                .isEqualTo(404);
    }

    @Test
    void creatingAReminderForAShortlistYouDoNotOwnIs404() {
        Long userId = insertUser("reminder-shortlist-owner@example.com");
        Long otherUserId = insertUser("reminder-shortlist-other@example.com");
        ShortlistDto otherShortlist = shortlistService.create(otherUserId, "Someone else's shortlist");

        assertThatThrownBy(() -> reminderService.create(
                        userId, "Check this shortlist", Instant.now().plus(Duration.ofDays(1)), null, otherShortlist.id()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
                .isEqualTo(404);
    }

    @Test
    void operatingOnAnotherUsersReminderIs404() {
        Long ownerId = insertUser("reminder-owner@example.com");
        Long attackerId = insertUser("reminder-attacker@example.com");
        ReminderDto reminder = reminderService.create(ownerId, "Follow up", Instant.now().plus(Duration.ofDays(1)), null, null);

        assertThatThrownBy(() -> reminderService.setCompleted(attackerId, reminder.id(), true))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
                .isEqualTo(404);
        assertThatThrownBy(() -> reminderService.delete(attackerId, reminder.id()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
                .isEqualTo(404);

        assertThat(reminderService.list(ownerId)).extracting(ReminderDto::id).containsExactly(reminder.id());
    }

    private Long insertUser(String email) {
        AppUser user = appUserRepository.save(new AppUser(email, "irrelevant-hash", "Test User", Instant.now(), Instant.now()));
        return user.getId();
    }
}
