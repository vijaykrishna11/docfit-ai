package com.docfitai.backend.insurance.connector;

import java.util.List;

/**
 * Abstraction over a payer-side provider network directory source. Implementations never get
 * called on a live user search request (CLAUDE.md 89) -- only from operator-triggered import
 * code that writes results into {@code provider_network_evidence}.
 */
public interface ProviderNetworkConnector {

    /** Stable code matching the {@code network_source.name} row this connector writes evidence against. */
    String sourceCode();

    /** Plans/networks this source currently knows about, for the import job to reconcile against {@code insurance_plan}/{@code insurance_network}. */
    List<DiscoveredPlan> discoverPlans();

    /** Raw participation records for one NPI. Empty list means "checked, found nothing" -- never null. */
    List<NetworkParticipationRecord> fetchProviderNetworkParticipation(String npi);

    ConnectorHealth healthCheck();
}
