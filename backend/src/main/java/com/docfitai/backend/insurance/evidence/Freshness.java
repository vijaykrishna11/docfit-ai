package com.docfitai.backend.insurance.evidence;

/** Freshness qualifier, only meaningful when status is EVIDENCE_FOUND. Never shown for other statuses. */
public enum Freshness {
    FRESH,
    AGING,
    STALE
}
