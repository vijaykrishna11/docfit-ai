package com.docfitai.backend.provider.ingestion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A bounded, meaningful provider-directory change (CLAUDE.md "Provider Change Event") -- never the full raw source record. */
@Entity
@Table(name = "provider_change_event")
public class ProviderChangeEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false)
    private ChangeType changeType;

    @Column(name = "provider_location_id")
    private Long providerLocationId;

    @Column(name = "old_value")
    private String oldValue;

    @Column(name = "new_value")
    private String newValue;

    @Column(name = "source_import_id")
    private Long sourceImportId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProviderChangeEvent() {
    }

    public ProviderChangeEvent(
            Long providerId,
            ChangeType changeType,
            Long providerLocationId,
            String oldValue,
            String newValue,
            Long sourceImportId,
            Instant createdAt) {
        this.providerId = providerId;
        this.changeType = changeType;
        this.providerLocationId = providerLocationId;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.sourceImportId = sourceImportId;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getProviderId() {
        return providerId;
    }

    public ChangeType getChangeType() {
        return changeType;
    }

    public Long getProviderLocationId() {
        return providerLocationId;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public Long getSourceImportId() {
        return sourceImportId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
