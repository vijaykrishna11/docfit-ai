package com.docfitai.backend.insurance.evidence;

/**
 * Machine-readable evidence states. {@code NO_EVIDENCE_FOUND} does NOT mean out-of-network -- a
 * directory can be incomplete, stale, or plan/location-specific. {@code STALE} is intentionally
 * absent here: staleness is a freshness qualifier computed at read time from {@code checked_at}
 * (see {@link Freshness}), not a stored observation, so it never goes out of sync with the clock.
 */
public enum NetworkEvidenceStatus {
    EVIDENCE_FOUND,
    NO_EVIDENCE_FOUND,
    SOURCE_UNAVAILABLE,
    MATCH_AMBIGUOUS,
    NOT_CHECKED
}
