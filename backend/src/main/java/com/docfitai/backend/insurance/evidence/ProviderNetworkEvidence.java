package com.docfitai.backend.insurance.evidence;

import com.docfitai.backend.insurance.InsuranceNetwork;
import com.docfitai.backend.insurance.InsurancePlan;
import com.docfitai.backend.insurance.NetworkSource;
import com.docfitai.backend.provider.Provider;
import com.docfitai.backend.provider.ProviderLocation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "provider_network_evidence")
public class ProviderNetworkEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @ManyToOne
    @JoinColumn(name = "insurance_network_id", nullable = false)
    private InsuranceNetwork network;

    @ManyToOne
    @JoinColumn(name = "insurance_plan_id")
    private InsurancePlan plan;

    /**
     * The specific practice location this evidence applies to, when the source data supports
     * that granularity. Null means the evidence is provider-wide (not tied to one office) --
     * never inferred/guessed onto a specific location (CLAUDE.md 8-9).
     */
    @ManyToOne
    @JoinColumn(name = "provider_location_id")
    private ProviderLocation providerLocation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NetworkEvidenceStatus status;

    @ManyToOne
    @JoinColumn(name = "source_id", nullable = false)
    private NetworkSource source;

    @Column(name = "source_provider_identifier")
    private String sourceProviderIdentifier;

    @Column(name = "matched_address_line1")
    private String matchedAddressLine1;

    @Column(name = "matched_city")
    private String matchedCity;

    @Column(name = "matched_state_code")
    private String matchedStateCode;

    @Column(name = "matched_postal_code")
    private String matchedPostalCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_method", nullable = false)
    private MatchMethod matchMethod;

    @Column(name = "first_seen_at", insertable = false, updatable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "checked_at")
    private Instant checkedAt;

    @Column(name = "source_last_updated_at")
    private Instant sourceLastUpdatedAt;

    protected ProviderNetworkEvidence() {
    }

    public ProviderNetworkEvidence(
            Provider provider,
            InsuranceNetwork network,
            InsurancePlan plan,
            ProviderLocation providerLocation,
            NetworkEvidenceStatus status,
            NetworkSource source,
            String sourceProviderIdentifier,
            String matchedAddressLine1,
            String matchedCity,
            String matchedStateCode,
            String matchedPostalCode,
            MatchMethod matchMethod,
            Instant checkedAt,
            Instant sourceLastUpdatedAt) {
        this.provider = provider;
        this.network = network;
        this.plan = plan;
        this.providerLocation = providerLocation;
        this.status = status;
        this.source = source;
        this.sourceProviderIdentifier = sourceProviderIdentifier;
        this.matchedAddressLine1 = matchedAddressLine1;
        this.matchedCity = matchedCity;
        this.matchedStateCode = matchedStateCode;
        this.matchedPostalCode = matchedPostalCode;
        this.matchMethod = matchMethod;
        this.checkedAt = checkedAt;
        this.lastSeenAt = checkedAt;
        this.sourceLastUpdatedAt = sourceLastUpdatedAt;
    }

    /** Re-confirms an existing observation: bumps last_seen_at/checked_at, updates status if it changed. */
    public void reconfirm(NetworkEvidenceStatus status, MatchMethod matchMethod, Instant checkedAt, Instant sourceLastUpdatedAt) {
        this.status = status;
        this.matchMethod = matchMethod;
        this.checkedAt = checkedAt;
        this.lastSeenAt = checkedAt;
        this.sourceLastUpdatedAt = sourceLastUpdatedAt;
    }

    public Long getId() {
        return id;
    }

    public Provider getProvider() {
        return provider;
    }

    public InsuranceNetwork getNetwork() {
        return network;
    }

    public InsurancePlan getPlan() {
        return plan;
    }

    public ProviderLocation getProviderLocation() {
        return providerLocation;
    }

    public NetworkEvidenceStatus getStatus() {
        return status;
    }

    public NetworkSource getSource() {
        return source;
    }

    public String getSourceProviderIdentifier() {
        return sourceProviderIdentifier;
    }

    public String getMatchedAddressLine1() {
        return matchedAddressLine1;
    }

    public String getMatchedCity() {
        return matchedCity;
    }

    public String getMatchedStateCode() {
        return matchedStateCode;
    }

    public String getMatchedPostalCode() {
        return matchedPostalCode;
    }

    public MatchMethod getMatchMethod() {
        return matchMethod;
    }

    public Instant getFirstSeenAt() {
        return firstSeenAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public Instant getCheckedAt() {
        return checkedAt;
    }

    public Instant getSourceLastUpdatedAt() {
        return sourceLastUpdatedAt;
    }
}
