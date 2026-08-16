package com.docfitai.backend.insurance.evidence;

/** Explainable match methodology -- never hidden from the evidence detail response. */
public enum MatchMethod {
    NPI_EXACT,
    NPI_AND_LOCATION,
    NPI_AND_POSTAL_CODE,
    ORGANIZATION_NPI,
    AMBIGUOUS
}
