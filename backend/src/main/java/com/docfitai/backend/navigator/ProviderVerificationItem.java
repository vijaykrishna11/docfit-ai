package com.docfitai.backend.navigator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A user's own "have I confirmed this yet" tracker for one administrative item on one provider.
 * {@code status = CONFIRMED_BY_USER} means only that this user says they confirmed it -- it is
 * never read back into provider/network-evidence data, and never visible to another user
 * (CLAUDE.md "User Confirmation Semantics").
 */
@Entity
@Table(name = "provider_verification_item")
public class ProviderVerificationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @Column(name = "provider_location_id")
    private Long providerLocationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_type", nullable = false)
    private VerificationType verificationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerificationItemStatus status;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProviderVerificationItem() {
    }

    public ProviderVerificationItem(
            Long userId,
            Long providerId,
            Long providerLocationId,
            VerificationType verificationType,
            VerificationItemStatus status,
            Instant confirmedAt,
            Instant updatedAt) {
        this.userId = userId;
        this.providerId = providerId;
        this.providerLocationId = providerLocationId;
        this.verificationType = verificationType;
        this.status = status;
        this.confirmedAt = confirmedAt;
        this.updatedAt = updatedAt;
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

    public Long getProviderLocationId() {
        return providerLocationId;
    }

    public VerificationType getVerificationType() {
        return verificationType;
    }

    public VerificationItemStatus getStatus() {
        return status;
    }

    public void setStatus(VerificationItemStatus status) {
        this.status = status;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(Instant confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
