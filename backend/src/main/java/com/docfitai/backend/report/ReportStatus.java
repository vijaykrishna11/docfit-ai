package com.docfitai.backend.report;

/** Matches the CHECK constraint on provider_data_report.status (V12). Reports are review signals only -- nothing reads this table to alter provider data automatically. */
public enum ReportStatus {
    NEW,
    REVIEWED,
    RESOLVED,
    DISMISSED
}
