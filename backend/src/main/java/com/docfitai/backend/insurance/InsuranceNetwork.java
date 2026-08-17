package com.docfitai.backend.insurance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "insurance_network")
public class InsuranceNetwork {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "payer_id", nullable = false)
    private Payer payer;

    @Column(name = "network_name", nullable = false)
    private String networkName;

    @Column(name = "external_network_identifier")
    private String externalNetworkIdentifier;

    @Column(nullable = false)
    private boolean active;

    protected InsuranceNetwork() {
    }

    public Long getId() {
        return id;
    }

    public Payer getPayer() {
        return payer;
    }

    public String getNetworkName() {
        return networkName;
    }

    public String getExternalNetworkIdentifier() {
        return externalNetworkIdentifier;
    }

    public boolean isActive() {
        return active;
    }
}
