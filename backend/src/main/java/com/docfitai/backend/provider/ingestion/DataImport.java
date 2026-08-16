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

/** Import provenance/history (CLAUDE.md 23-24) -- counts and status only, never raw source payloads. */
@Entity
@Table(name = "data_import")
public class DataImport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String source;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DataImportStatus status;

    @Column(name = "records_read", nullable = false)
    private int recordsRead;

    @Column(name = "providers_created", nullable = false)
    private int providersCreated;

    @Column(name = "providers_updated", nullable = false)
    private int providersUpdated;

    @Column(name = "locations_created", nullable = false)
    private int locationsCreated;

    @Column(name = "locations_updated", nullable = false)
    private int locationsUpdated;

    @Column(name = "records_failed", nullable = false)
    private int recordsFailed;

    protected DataImport() {
    }

    public DataImport(String source, Instant startedAt) {
        this.source = source;
        this.startedAt = startedAt;
        this.status = DataImportStatus.RUNNING;
    }

    public void recordUpsert(ProviderUpsertService.UpsertOutcome outcome) {
        recordsRead++;
        if (outcome.providerCreated()) {
            providersCreated++;
        } else {
            providersUpdated++;
        }
        locationsCreated += outcome.locationsCreated();
        locationsUpdated += outcome.locationsUpdated();
    }

    public void recordFailure() {
        recordsRead++;
        recordsFailed++;
    }

    public void complete(Instant completedAt) {
        this.completedAt = completedAt;
        this.status = recordsFailed > 0 && (providersCreated > 0 || providersUpdated > 0) ? DataImportStatus.PARTIAL : DataImportStatus.COMPLETED;
    }

    public void fail(Instant completedAt) {
        this.completedAt = completedAt;
        this.status = DataImportStatus.FAILED;
    }

    public Long getId() {
        return id;
    }

    public String getSource() {
        return source;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public DataImportStatus getStatus() {
        return status;
    }

    public int getRecordsRead() {
        return recordsRead;
    }

    public int getProvidersCreated() {
        return providersCreated;
    }

    public int getProvidersUpdated() {
        return providersUpdated;
    }

    public int getLocationsCreated() {
        return locationsCreated;
    }

    public int getLocationsUpdated() {
        return locationsUpdated;
    }

    public int getRecordsFailed() {
        return recordsFailed;
    }
}
