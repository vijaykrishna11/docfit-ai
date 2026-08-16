package com.docfitai.backend.report;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A directory-data correction report (CLAUDE.md "Data Correction Reporting"). Review signal only -- never applied automatically. */
@Entity
@Table(name = "provider_data_report")
public class ProviderDataReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @Column(name = "provider_location_id")
    private Long providerLocationId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "report_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private ReportType reportType;

    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProviderDataReport() {
    }

    public ProviderDataReport(
            Long providerId, Long providerLocationId, Long userId, ReportType reportType, String comment, Instant createdAt) {
        this.providerId = providerId;
        this.providerLocationId = providerLocationId;
        this.userId = userId;
        this.reportType = reportType;
        this.comment = comment;
        this.status = ReportStatus.NEW;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getProviderId() {
        return providerId;
    }

    public Long getProviderLocationId() {
        return providerLocationId;
    }

    public Long getUserId() {
        return userId;
    }

    public ReportType getReportType() {
        return reportType;
    }

    public String getComment() {
        return comment;
    }

    public ReportStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
