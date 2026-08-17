package com.docfitai.backend.insurance.connector;

import java.time.Instant;

/**
 * One raw match as reported by a source, before DocFit AI compares it against its own provider
 * record to decide a {@link com.docfitai.backend.insurance.evidence.MatchMethod}. Address fields
 * are null when the source doesn't report a location -- the import service must never invent one.
 */
public record NetworkParticipationRecord(
        String npi,
        String externalNetworkId,
        String externalPlanId,
        String addressLine1,
        String city,
        String stateCode,
        String postalCode,
        Instant sourceLastUpdatedAt) {
}
