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

    public Map<Long, NetworkEvidenceSummaryDto> summarizeForProviders(List<Long> providerIds, Long planId) {
        if (providerIds.isEmpty() || planId == null) {
            return Map.of();
        }
        InsurancePlan plan = planRepository.findById(planId).orElse(null);
        if (plan == null) {
            return Map.of();
        }
        Map<Long, ProviderNetworkEvidence> byProvider = resolveEvidence(providerIds, plan);

        Map<Long, NetworkEvidenceSummaryDto> result = new HashMap<>();
        for (Long providerId : providerIds) {
            result.put(providerId, toSummary(byProvider.get(providerId), plan));
        }
        return result;
    }

    public NetworkEvidenceDetailDto lookup(Long providerId, InsurancePlan plan) {
        Map<Long, ProviderNetworkEvidence> byProvider = resolveEvidence(List.of(providerId), plan);
        return toDetail(providerId, byProvider.get(providerId), plan);
    }

    private Map<Long, ProviderNetworkEvidence> resolveEvidence(List<Long> providerIds, InsurancePlan plan) {
        Map<Long, ProviderNetworkEvidence> byProvider = new HashMap<>();
        for (ProviderNetworkEvidence e : evidenceRepository.findByProviderIdsAndPlanId(providerIds, plan.getId())) {
            byProvider.put(e.getProvider().getId(), e);
        }
        List<Long> networkIds = plan.getNetworks().stream().map(InsuranceNetwork::getId).toList();
        if (!networkIds.isEmpty()) {
            for (ProviderNetworkEvidence e : evidenceRepository.findByProviderIdsAndNetworkIdsNoPlan(providerIds, networkIds)) {
                byProvider.putIfAbsent(e.getProvider().getId(), e);
            }
        }
        return byProvider;
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
