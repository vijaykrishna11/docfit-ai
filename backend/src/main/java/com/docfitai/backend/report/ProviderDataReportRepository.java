package com.docfitai.backend.report;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * No findAll/list-by-provider query is exposed to any controller -- reports are never surfaced
 * back through the API (CLAUDE.md "Directory Report API": "Do NOT expose GET all reports
 * publicly"). Review happens via direct operator query against this table (see
 * docs/directory-corrections.md), not through the application.
 */
public interface ProviderDataReportRepository extends JpaRepository<ProviderDataReport, Long> {
}
