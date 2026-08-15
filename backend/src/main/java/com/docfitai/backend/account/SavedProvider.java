package com.docfitai.backend.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** An explicitly user-saved provider ("shortlist" entry) -- never auto-populated. */
@Entity
@Table(name = "saved_provider")
public class SavedProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SavedProvider() {
    }

    public SavedProvider(Long userId, Long providerId, Instant createdAt) {
        this.userId = userId;
        this.providerId = providerId;
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

    public Instant getCreatedAt() {
        return createdAt;
    }
}
