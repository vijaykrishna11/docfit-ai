package com.docfitai.backend.navigator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A lightweight, user-created, in-app-only reminder (CLAUDE.md "Follow-Up Reminder
 * Architecture"). No push/SMS/email -- surfaced only within DocFit AI itself. {@code providerId}
 * and {@code shortlistId} are both optional and independent of each other.
 */
@Entity
@Table(name = "user_reminder")
public class UserReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "provider_id")
    private Long providerId;

    @Column(name = "shortlist_id")
    private Long shortlistId;

    @Column(nullable = false)
    private String title;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected UserReminder() {
    }

    public UserReminder(
            Long userId, Long providerId, Long shortlistId, String title, Instant dueAt, Instant completedAt, Instant createdAt) {
        this.userId = userId;
        this.providerId = providerId;
        this.shortlistId = shortlistId;
        this.title = title;
        this.dueAt = dueAt;
        this.completedAt = completedAt;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getProviderId() {
        return providerId;
    }

    public Long getShortlistId() {
        return shortlistId;
    }

    public String getTitle() {
        return title;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
