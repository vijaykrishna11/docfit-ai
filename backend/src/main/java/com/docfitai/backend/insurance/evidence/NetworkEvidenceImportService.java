package com.docfitai.backend.insurance.evidence;

import com.docfitai.backend.insurance.InsuranceNetwork;
import com.docfitai.backend.insurance.InsurancePlan;
import com.docfitai.backend.insurance.NetworkSource;
import com.docfitai.backend.insurance.connector.NetworkParticipationRecord;
import com.docfitai.backend.provider.Provider;
import com.docfitai.backend.provider.ProviderLocation;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns a connector's raw {@link NetworkParticipationRecord} matches into a stored evidence
 * observation, deciding the {@link MatchMethod} -- and, where possible, which specific
 * {@link ProviderLocation} the evidence applies to -- by comparing the source's reported location
 * against DocFit AI's own provider location records, never trusting the source's self-reported
 * match quality (CLAUDE.md 11). A location is only ever bound when it can be deterministically
 * resolved (CLAUDE.md 8) -- ambiguous or address-less matches stay provider-wide (null location).
 * Never called from a live search request; only from operator-triggered import code (CLAUDE.md 89).
 */
@Service
public class NetworkEvidenceImportService {

    private final ProviderNetworkEvidenceRepository evidenceRepository;

    public NetworkEvidenceImportService(ProviderNetworkEvidenceRepository evidenceRepository) {
        this.evidenceRepository = evidenceRepository;
    }

    @Transactional
    public ProviderNetworkEvidence recordObservation(
            Provider provider,
            List<ProviderLocation> providerLocations,
            InsuranceNetwork network,
            InsurancePlan plan,
            NetworkSource source,
            List<NetworkParticipationRecord> matches,
            Instant checkedAt) {
        Long planId = plan == null ? null : plan.getId();
        Observation observation = classify(providerLocations, matches);
        Long locationId = observation.matchedLocation() == null ? null : observation.matchedLocation().getId();

        ProviderNetworkEvidence existing = evidenceRepository
                .findByProviderIdAndNetworkIdAndPlanIdAndSourceIdAndProviderLocationId(
                        provider.getId(), network.getId(), planId, source.getId(), locationId)
                .orElse(null);

        if (existing != null) {
            existing.reconfirm(observation.status(), observation.matchMethod(), checkedAt, observation.sourceLastUpdatedAt());
            return evidenceRepository.save(existing);
        }
        ProviderNetworkEvidence created = new ProviderNetworkEvidence(
                provider,
                network,
                plan,
                observation.matchedLocation(),
                observation.status(),
                source,
                provider.getNpiNumber(),
                observation.addressLine1(),
                observation.city(),
                observation.stateCode(),
                observation.postalCode(),
                observation.matchMethod(),
                checkedAt,
                observation.sourceLastUpdatedAt());
        return evidenceRepository.save(created);
    }

    private Observation classify(List<ProviderLocation> providerLocations, List<NetworkParticipationRecord> matches) {
        if (matches.isEmpty()) {
            return new Observation(NetworkEvidenceStatus.NO_EVIDENCE_FOUND, MatchMethod.NPI_EXACT, null, null, null, null, null, null);
        }
        if (matches.size() > 1) {
            NetworkParticipationRecord first = matches.get(0);
            return new Observation(
                    NetworkEvidenceStatus.MATCH_AMBIGUOUS,
                    MatchMethod.AMBIGUOUS,
                    first.addressLine1(),
                    first.city(),
                    first.stateCode(),
                    first.postalCode(),
                    first.sourceLastUpdatedAt(),
                    null);
        }
        NetworkParticipationRecord match = matches.get(0);
        LocationMatch locationMatch = matchLocation(providerLocations, match);
        return new Observation(
                NetworkEvidenceStatus.EVIDENCE_FOUND,
                locationMatch.method(),
                match.addressLine1(),
                match.city(),
                match.stateCode(),
                match.postalCode(),
                match.sourceLastUpdatedAt(),
                locationMatch.location());
    }

    /**
     * Prefers an exact address+city+postal match to one specific location; falls back to a
     * postal-only match; never binds to a location it can't justify (CLAUDE.md 8-9).
     */
    private static LocationMatch matchLocation(List<ProviderLocation> providerLocations, NetworkParticipationRecord match) {
        if (match.postalCode() == null) {
            return new LocationMatch(MatchMethod.NPI_EXACT, null);
        }
        for (ProviderLocation candidate : providerLocations) {
            boolean postalMatches = match.postalCode().equals(candidate.getPostalCode());
            boolean lineMatches = match.addressLine1() != null && match.addressLine1().equalsIgnoreCase(candidate.getAddressLine1());
            boolean cityMatches = Objects.equals(match.city(), candidate.getCity());
            if (postalMatches && lineMatches && cityMatches) {
                return new LocationMatch(MatchMethod.NPI_AND_LOCATION, candidate);
            }
        }
        for (ProviderLocation candidate : providerLocations) {
            if (match.postalCode().equals(candidate.getPostalCode())) {
                return new LocationMatch(MatchMethod.NPI_AND_POSTAL_CODE, candidate);
            }
        }
        return new LocationMatch(MatchMethod.NPI_EXACT, null);
    }

    private record LocationMatch(MatchMethod method, ProviderLocation location) {
    }

    private record Observation(
            NetworkEvidenceStatus status,
            MatchMethod matchMethod,
            String addressLine1,
            String city,
            String stateCode,
            String postalCode,
            Instant sourceLastUpdatedAt,
            ProviderLocation matchedLocation) {
    }
}
