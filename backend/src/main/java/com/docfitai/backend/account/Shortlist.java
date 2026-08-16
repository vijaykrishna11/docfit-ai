package com.docfitai.backend.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A named, user-owned grouping of saved providers (CLAUDE.md "Shortlists V2"). Distinct from
 * {@link SavedProvider} (the default, one-click "Saved providers" list) -- a shortlist is an
 * optional additional organization layer a provider can belong to zero or more of. Names are
 * treated as private user data (may contain sensitive context, e.g. "Parents") -- never exposed
 * outside the owning user's own authenticated requests.
 */
@Entity
@Table(name = "provider_shortlist")
public class Shortlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Shortlist() {
    }

    public Shortlist(Long userId, String name, Instant createdAt, Instant updatedAt) {
        this.userId = userId;
        this.name = name;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
