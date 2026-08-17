package com.docfitai.backend.provider;

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
 * Provider identity -- NOT location. Practice addresses/phones live in {@link ProviderLocation}
 * (a provider may have zero-to-many). The legacy single-address/phone/coordinate columns still
 * exist on the underlying {@code provider} table for now (loosened to nullable, unused by this
 * entity) -- see {@code V8__create_provider_location.sql} and
 * docs/provider-data-platform.md ("Migration strategy") for why their removal is deliberately
 * deferred to a later migration rather than done in the same step as this refactor.
 */
@Entity
@Table(name = "provider")
public class Provider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "npi_number", nullable = false, unique = true, length = 10)
    private String npiNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false)
    private ProviderEntityType entityType;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "organization_name")
    private String organizationName;

    // Populated by the database's DEFAULT now() -- never set from Java -- so it always reflects
    // the true moment the row was written, for both the historical backfill and future imports.
    @Column(name = "imported_at", insertable = false, updatable = false)
    private Instant importedAt;

    protected Provider() {
    }

    public Provider(String npiNumber, ProviderEntityType entityType, String firstName, String lastName, String organizationName) {
        this.npiNumber = npiNumber;
        this.entityType = entityType;
        this.firstName = firstName;
        this.lastName = lastName;
        this.organizationName = organizationName;
    }

    public Long getId() {
        return id;
    }

    public String getNpiNumber() {
        return npiNumber;
    }

    public ProviderEntityType getEntityType() {
        return entityType;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getOrganizationName() {
        return organizationName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public void setOrganizationName(String organizationName) {
        this.organizationName = organizationName;
    }

    public Instant getImportedAt() {
        return importedAt;
    }
}
