package com.docfitai.backend.insurance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "insurance_plan")
public class InsurancePlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "payer_id", nullable = false)
    private Payer payer;

    @Column(name = "plan_name", nullable = false)
    private String planName;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type", nullable = false)
    private PlanType planType;

    @Column(name = "external_plan_identifier")
    private String externalPlanIdentifier;

    @Column(nullable = false)
    private boolean active;

    // EAGER is deliberate: a plan participates in a handful of networks at most, and callers
    // (e.g. the network-evidence lookup) commonly receive this entity already detached from the
    // session that loaded it, so a lazy proxy would fail rather than just being slightly less
    // efficient.
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "plan_network",
            joinColumns = @JoinColumn(name = "plan_id"),
            inverseJoinColumns = @JoinColumn(name = "network_id"))
    private Set<InsuranceNetwork> networks = new LinkedHashSet<>();

    protected InsurancePlan() {
    }

    public Long getId() {
        return id;
    }

    public Payer getPayer() {
        return payer;
    }

    public String getPlanName() {
        return planName;
    }

    public PlanType getPlanType() {
        return planType;
    }

    public String getExternalPlanIdentifier() {
        return externalPlanIdentifier;
    }

    public boolean isActive() {
        return active;
    }

    public Set<InsuranceNetwork> getNetworks() {
        return networks;
    }
}
