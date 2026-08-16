package com.docfitai.backend.insurance;

public enum SourceType {
    FHIR_API,
    JSON_API,
    MACHINE_READABLE_FILE,
    /** Synthetic demo/test evidence only -- never presented as real payer data. See CLAUDE.md 42. */
    MANUAL_DEMO_REFERENCE
}
