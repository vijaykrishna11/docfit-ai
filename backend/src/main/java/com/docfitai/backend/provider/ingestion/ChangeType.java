package com.docfitai.backend.provider.ingestion;

/**
 * Allowlisted, meaningful change types (CLAUDE.md "Change Detection": "Do not record every
 * unchanged field"). Deliberately a subset of the directive's full list -- only the changes this
 * importer architecture can actually detect given its current no-delete, additive-update
 * semantics (see docs/provider-ingestion.md "Change detection"). Not included, and why:
 *
 * <ul>
 *   <li>{@code LOCATION_REMOVED_FROM_SOURCE}, {@code TAXONOMY_REMOVED}, {@code STATUS_CHANGED} --
 *       would require reconciliation logic (comparing a "complete" import's full result set
 *       against existing rows) that is deliberately not implemented this phase, because a
 *       {@code PARTIAL}-scope import can never safely tell "genuinely removed" apart from "just
 *       not in this bounded batch" (CLAUDE.md "Partial Import Safety").
 *   <li>{@code LOCATION_CHANGED} (address) -- the upsert path matches an existing location by its
 *       normalized address identity; a genuine address change is therefore always a new location
 *       row ({@code LOCATION_ADDED}), not an in-place update, so a separate "address changed"
 *       event type would never fire.
 * </ul>
 */
public enum ChangeType {
    PROVIDER_NAME_CHANGED,
    ORGANIZATION_NAME_CHANGED,
    LOCATION_ADDED,
    PHONE_CHANGED,
    TAXONOMY_ADDED
}
