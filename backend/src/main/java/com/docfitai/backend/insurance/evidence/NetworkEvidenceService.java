package com.docfitai.backend.insurance.evidence;

import com.docfitai.backend.insurance.InsuranceNetwork;
import com.docfitai.backend.insurance.InsurancePlan;
import com.docfitai.backend.insurance.InsurancePlanRepository;
import com.docfitai.backend.insurance.dto.NetworkEvidenceDetailDto;
import com.docfitai.backend.insurance.dto.NetworkEvidenceSummaryDto;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Always reads local, previously-imported evidence -- never calls a connector on a live search
 * request (CLAUDE.md 89). Always returns a result for every provider asked about; a missing or
 * disabled source becomes an explicit status (SOURCE_UNAVAILABLE/NOT_CHECKED), never an
 * exception, so provider search is never broken by insurance data (CLAUDE.md 44).
 *
 * <p>Location-aware (CLAUDE.md 9): when a specific practice location is being displayed, evidence
 * bound to that exact location is preferred; provider-wide evidence (no location) is the
 * fallback. Evidence recorded for a *different* specific location is never silently applied to
 * the location actually shown.
 */
@Service
@Transactional(readOnly = true)
public class NetworkEvidenceService {

    private static final List<String> LIMITATIONS = List.of(
            "Network directory participation may change and does not guarantee coverage or payment.",
            "Confirm eligibility and benefits directly with your insurer before your visit.",
            "Absence of evidence does not necessarily mean a provider is out of network -- directory data can be incomplete, stale, or specific to another location.");

    private final ProviderNetworkEvidenceRepository evidenceRepository;
    private final InsurancePlanRepository planRepository;
    private final FreshnessProperties freshnessProperties;

    public NetworkEvidenceService(
            ProviderNetworkEvidenceRepository evidenceRepository,
            InsurancePlanRepository planRepository,
            FreshnessProperties freshnessProperties) {
        this.evidenceRepository = evidenceRepository;
        this.planRepository = planRepository;
        this.freshnessProperties = freshnessProperties;
    }

    /**
     * @param providerLocations providerId -> the specific location id currently being displayed
     *     for that provider (nullable per-entry when no location context is known)
     */
    public Map<Long, NetworkEvidenceSummaryDto> summarizeForProviders(Map<Long, Long> providerLocations, Long planId) {
        if (providerLocations.isEmpty() || planId == null) {
            return Map.of();
        }
        InsurancePlan plan = planRepository.findById(planId).orElse(null);
        if (plan == null) {
            return Map.of();
        }
        Map<Long, List<ProviderNetworkEvidence>> byProvider = resolveEvidence(providerLocations.keySet().stream().toList(), plan);

        Map<Long, NetworkEvidenceSummaryDto> result = new HashMap<>();
        for (Map.Entry<Long, Long> entry : providerLocations.entrySet()) {
            ProviderNetworkEvidence evidence = pickForLocation(byProvider.get(entry.getKey()), entry.getValue());
            result.put(entry.getKey(), toSummary(evidence, plan));
        }
        return result;
    }

    public NetworkEvidenceDetailDto lookup(Long providerId, Long locationId, InsurancePlan plan) {
        Map<Long, List<ProviderNetworkEvidence>> byProvider = resolveEvidence(List.of(providerId), plan);
        ProviderNetworkEvidence evidence = pickForLocation(byProvider.get(providerId), locationId);
        return toDetail(providerId, evidence, plan);
    }

    private Map<Long, List<ProviderNetworkEvidence>> resolveEvidence(List<Long> providerIds, InsurancePlan plan) {
        Map<Long, List<ProviderNetworkEvidence>> byProvider = new HashMap<>();
        for (ProviderNetworkEvidence e : evidenceRepository.findByProviderIdsAndPlanId(providerIds, plan.getId())) {
            byProvider.computeIfAbsent(e.getProvider().getId(), key -> new java.util.ArrayList<>()).add(e);
        }
        List<Long> networkIds = plan.getNetworks().stream().map(InsuranceNetwork::getId).toList();
        if (!networkIds.isEmpty()) {
            for (ProviderNetworkEvidence e : evidenceRepository.findByProviderIdsAndNetworkIdsNoPlan(providerIds, networkIds)) {
                // Plan-specific evidence always wins over network-level fallback for a given provider.
                if (!byProvider.containsKey(e.getProvider().getId())) {
                    byProvider.computeIfAbsent(e.getProvider().getId(), key -> new java.util.ArrayList<>()).add(e);
                }
            }
        }
        return byProvider;
    }

    /**
     * Prefers evidence bound to the exact location being displayed; falls back to provider-wide
     * evidence (no location). When the caller has no specific location context at all
     * ({@code locationId == null}) and every remaining candidate happens to point at the very
     * same single location, it's unambiguous to show it anyway -- but if candidates span more
     * than one distinct location, evidence is never guessed across locations (CLAUDE.md 9).
     */
    private static ProviderNetworkEvidence pickForLocation(List<ProviderNetworkEvidence> candidates, Long locationId) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        if (locationId != null) {
            for (ProviderNetworkEvidence e : candidates) {
                if (e.getProviderLocation() != null && e.getProviderLocation().getId().equals(locationId)) {
                    return e;
                }
            }
        }
        for (ProviderNetworkEvidence e : candidates) {
            if (e.getProviderLocation() == null) {
                return e;
            }
        }
        if (locationId == null) {
            long distinctLocations =
                    candidates.stream().map(e -> e.getProviderLocation().getId()).distinct().count();
            if (distinctLocations == 1) {
                return candidates.get(0);
            }
        }
        return null;
    }

    private NetworkEvidenceSummaryDto toSummary(ProviderNetworkEvidence evidence, InsurancePlan plan) {
        if (evidence == null) {
            return new NetworkEvidenceSummaryDto(NetworkEvidenceStatus.NOT_CHECKED.name(), null, plan.getPlanName(), null, false, null);
        }
        NetworkEvidenceStatus status = effectiveStatus(evidence);
        Freshness freshness = status == NetworkEvidenceStatus.EVIDENCE_FOUND ? freshnessFor(evidence.getCheckedAt()) : null;
        return new NetworkEvidenceSummaryDto(
                status.name(),
                freshness == null ? null : freshness.name(),
                evidence.getPlan() != null ? evidence.getPlan().getPlanName() : plan.getPlanName(),
                evidence.getNetwork().getNetworkName(),
                evidence.getSource().isSynthetic(),
                evidence.getCheckedAt());
    }

    private NetworkEvidenceDetailDto toDetail(Long providerId, ProviderNetworkEvidence evidence, InsurancePlan plan) {
        if (evidence == null) {
            return new NetworkEvidenceDetailDto(
                    providerId,
                    plan.getId(),
                    plan.getPlanName(),
                    null,
                    plan.getPayer().getName(),
                    NetworkEvidenceStatus.NOT_CHECKED.name(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    false,
                    null,
                    null,
                    LIMITATIONS);
        }
        NetworkEvidenceStatus status = effectiveStatus(evidence);
        Freshness freshness = status == NetworkEvidenceStatus.EVIDENCE_FOUND ? freshnessFor(evidence.getCheckedAt()) : null;
        return new NetworkEvidenceDetailDto(
                providerId,
                plan.getId(),
                plan.getPlanName(),
                evidence.getNetwork().getNetworkName(),
                evidence.getNetwork().getPayer().getName(),
                status.name(),
                freshness == null ? null : freshness.name(),
                evidence.getMatchedAddressLine1(),
                evidence.getMatchedCity(),
                evidence.getMatchedStateCode(),
                evidence.getMatchedPostalCode(),
                evidence.getMatchMethod().name(),
                evidence.getSource().getName(),
                evidence.getSource().getSourceType().name(),
                evidence.getSource().isSynthetic(),
                evidence.getCheckedAt(),
                evidence.getFirstSeenAt(),
                LIMITATIONS);
    }

    private static NetworkEvidenceStatus effectiveStatus(ProviderNetworkEvidence evidence) {
        return evidence.getSource().isActive() ? evidence.getStatus() : NetworkEvidenceStatus.SOURCE_UNAVAILABLE;
    }

    private Freshness freshnessFor(Instant checkedAt) {
        if (checkedAt == null) {
            return null;
        }
        long days = Duration.between(checkedAt, Instant.now()).toDays();
        if (days <= freshnessProperties.getFreshDays()) {
            return Freshness.FRESH;
        }
        if (days <= freshnessProperties.getAgingDays()) {
            return Freshness.AGING;
        }
        return Freshness.STALE;
    }
}
