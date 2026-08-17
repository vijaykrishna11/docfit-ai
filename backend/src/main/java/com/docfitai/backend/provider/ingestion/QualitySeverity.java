package com.docfitai.backend.provider.ingestion;

/**
 * CLAUDE.md "Quality Severity": ERROR for a genuine defect (invalid NPI format, unusable
 * identity), WARNING for something worth an operator's attention but not wrong per se (missing an
 * optional field like a postal code), INFO for a low-priority observation. Never used to reject
 * or delete data -- advisory only (CLAUDE.md 25).
 */
public enum QualitySeverity {
    ERROR,
    WARNING,
    INFO
}
