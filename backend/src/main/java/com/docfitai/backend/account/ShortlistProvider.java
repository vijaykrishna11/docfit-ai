package com.docfitai.backend.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** One provider's membership in one shortlist. Uniqueness enforced at the DB level (CLAUDE.md "Shortlist DB Constraints"). */
@Entity
@Table(name = "shortlist_provider")
public class ShortlistProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shortlist_id", nullable = false)
    private Long shortlistId;

    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ShortlistProvider() {
    }

    public ShortlistProvider(Long shortlistId, Long providerId, Instant createdAt) {
        this.shortlistId = shortlistId;
        this.providerId = providerId;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getShortlistId() {
        return shortlistId;
    }

    public Long getProviderId() {
        return providerId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
