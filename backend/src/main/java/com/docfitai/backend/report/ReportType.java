package com.docfitai.backend.report;

/**
 * Allowlisted directory-data correction categories (CLAUDE.md "Data Correction Reporting").
 * Deliberately does not include anything clinical (diagnosis, reason for visit, medical history,
 * treatment, medication) -- this is a directory-data correction feature, not a health intake form.
 */
public enum ReportType {
    WRONG_ADDRESS,
    WRONG_PHONE_NUMBER,
    PROVIDER_NOT_AT_LOCATION,
    NAME_APPEARS_INCORRECT,
    SPECIALTY_APPEARS_INCORRECT,
    DUPLICATE_PROVIDER_OR_LOCATION,
    INSURANCE_INFO_APPEARS_INCORRECT,
    OTHER
}
