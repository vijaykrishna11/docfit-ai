package com.docfitai.backend.navigator;

import com.docfitai.backend.account.ShortlistRepository;
import com.docfitai.backend.navigator.dto.ReminderDto;
import com.docfitai.backend.provider.ProviderRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * In-app-only follow-up reminders (CLAUDE.md "Follow-Up Reminder Architecture"): no push/SMS/
 * email integration. A reminder in the past is accepted and simply renders as immediately
 * overdue (CLAUDE.md "Reminder Date Validation" -- deliberate choice, documented in
 * docs/reminders.md); only an absurdly far-future date (more than 5 years out) is rejected, to
 * catch client-side date-math mistakes without inventing an arbitrary "too soon" restriction.
 */
@Service
public class ReminderService {

    private static final Duration MAX_FUTURE = Duration.ofDays(365L * 5);

    private static final String LIST_QUERY =
            """
            SELECT r.id, r.title, r.due_at, r.completed_at, r.created_at, r.provider_id, r.shortlist_id,
                   p.entity_type, p.first_name, p.last_name, p.organization_name, s.name AS shortlist_name
            FROM user_reminder r
            LEFT JOIN provider p ON p.id = r.provider_id
            LEFT JOIN provider_shortlist s ON s.id = r.shortlist_id
            WHERE r.user_id = ?
            ORDER BY (r.completed_at IS NOT NULL), r.due_at ASC
            """;

    private final UserReminderRepository reminderRepository;
    private final ProviderRepository providerRepository;
    private final ShortlistRepository shortlistRepository;
    private final JdbcTemplate jdbcTemplate;

    public ReminderService(
            UserReminderRepository reminderRepository,
            ProviderRepository providerRepository,
            ShortlistRepository shortlistRepository,
            JdbcTemplate jdbcTemplate) {
        this.reminderRepository = reminderRepository;
        this.providerRepository = providerRepository;
        this.shortlistRepository = shortlistRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ReminderDto> list(Long userId) {
        return jdbcTemplate.query(LIST_QUERY, (rs, rowNum) -> {
            Long providerId = rs.getLong("provider_id");
            boolean hasProvider = !rs.wasNull();
            String providerName = hasProvider ? providerDisplayName(rs) : null;
            Long shortlistId = rs.getLong("shortlist_id");
            boolean hasShortlist = !rs.wasNull();
            java.sql.Timestamp completedAt = rs.getTimestamp("completed_at");
            return new ReminderDto(
                    rs.getLong("id"),
                    rs.getString("title"),
                    rs.getTimestamp("due_at").toInstant(),
                    completedAt == null ? null : completedAt.toInstant(),
                    hasProvider ? providerId : null,
                    providerName,
                    hasShortlist ? shortlistId : null,
                    rs.getString("shortlist_name"),
                    rs.getTimestamp("created_at").toInstant());
        }, userId);
    }

    public List<ReminderDto> upcoming(Long userId, int limit) {
        return list(userId).stream().filter(r -> r.completedAt() == null).limit(limit).toList();
    }

    @Transactional
    public ReminderDto create(Long userId, String title, Instant dueAt, Long providerId, Long shortlistId) {
        if (dueAt.isAfter(Instant.now().plus(MAX_FUTURE))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reminder date is too far in the future.");
        }
        if (providerId != null && !providerRepository.existsById(providerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider not found.");
        }
        if (shortlistId != null && shortlistRepository.findByIdAndUserId(shortlistId, userId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Shortlist not found.");
        }
        UserReminder saved =
                reminderRepository.save(new UserReminder(userId, providerId, shortlistId, title.trim(), dueAt, null, Instant.now()));
        return list(userId).stream().filter(r -> r.id().equals(saved.getId())).findFirst().orElseThrow();
    }

    @Transactional
    public void setCompleted(Long userId, Long reminderId, boolean completed) {
        UserReminder reminder = reminderRepository
                .findByIdAndUserId(reminderId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reminder not found."));
        reminder.setCompletedAt(completed ? Instant.now() : null);
    }

    @Transactional
    public void delete(Long userId, Long reminderId) {
        UserReminder reminder = reminderRepository
                .findByIdAndUserId(reminderId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reminder not found."));
        reminderRepository.delete(reminder);
    }

    private static String providerDisplayName(java.sql.ResultSet rs) throws java.sql.SQLException {
        String organizationName = rs.getString("organization_name");
        if (organizationName != null && !organizationName.isBlank()) {
            return organizationName;
        }
        String first = rs.getString("first_name");
        String last = rs.getString("last_name");
        return ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
    }
}
