package com.docfitai.backend.insurance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "network_source")
public class NetworkSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @jakarta.persistence.JoinColumn(name = "payer_id", nullable = false)
    private Payer payer;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private SourceType sourceType;

    @Column(nullable = false)
    private String name;

    @Column(name = "base_url_reference")
    private String baseUrlReference;

    private String format;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "last_successful_check")
    private Instant lastSuccessfulCheck;

    protected NetworkSource() {
    }

    public Long getId() {
        return id;
    }

    public Payer getPayer() {
        return payer;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isSynthetic() {
        return sourceType == SourceType.MANUAL_DEMO_REFERENCE;
    }
}
