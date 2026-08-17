package com.docfitai.backend.navigator;

/**
 * A fixed, allowlisted set of administrative statuses a user can assign to a provider they're
 * considering (CLAUDE.md "Provider Navigation Status"). Deliberately nonclinical -- never
 * "recommended", "approved", or a medical judgement. An unknown value is rejected by JSON
 * deserialization before any application code runs, same pattern as {@code ReportType}.
 *
 * <p>{@code ARCHIVED} replaces the directive's optional "NOT_A_FIT" (CLAUDE.md "'Not a fit'":
 * "Prefer ARCHIVED if product is cleaner") -- it lets a user stop seeing a provider in their
 * active navigator list without DocFit ever recording or interpreting *why*.
 */
public enum NavigationStatus {
    SAVED,
    TO_CONTACT,
    CONTACTED,
    VERIFYING_DETAILS,
    SHORTLISTED,
    ARCHIVED
}
