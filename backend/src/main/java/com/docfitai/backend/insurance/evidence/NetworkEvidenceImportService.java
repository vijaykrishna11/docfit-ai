package com.docfitai.backend.insurance.evidence;

import com.docfitai.backend.insurance.InsuranceNetwork;
import com.docfitai.backend.insurance.InsurancePlan;
import com.docfitai.backend.insurance.NetworkSource;
import com.docfitai.backend.insurance.connector.NetworkParticipationRecord;
import com.docfitai.backend.provider.Provider;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns a connector's raw {@link NetworkParticipationRecord} matches into a stored evidence
 * observation, deciding the {@link MatchMethod} by comparing the source's reported location
 * against DocFit AI's own provider record -- never trusting the source's self-reported match
 * quality (CLAUDE.md 11). Never called from a live search request; only from operator-triggered
 * import code (CLAUDE.md 89).
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
            InsuranceNetwork network,
            InsurancePlan plan,
            NetworkSource source,
            List<NetworkParticipationRecord> matches,
            Instant checkedAt) {
        Long planId = plan == null ? null : plan.getId();
        ProviderNetworkEvidence existing = evidenceRepository
                .findByProviderIdAndNetworkIdAndPlanIdAndSourceId(provider.getId(), network.getId(), planId, source.getId())
                .orElse(null);

        Observation observation = classify(provider, matches);

        if (existing != null) {
            existing.reconfirm(observation.status(), observation.matchMethod(), checkedAt, observation.sourceLastUpdatedAt());
            return evidenceRepository.save(existing);
        }
        ProviderNetworkEvidence created = new ProviderNetworkEvidence(
                provider,
                network,
                plan,
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

    private Observation classify(Provider provider, List<NetworkParticipationRecord> matches) {
        if (matches.isEmpty()) {
            return new Observation(NetworkEvidenceStatus.NO_EVIDENCE_FOUND, MatchMethod.NPI_EXACT, null, null, null, null, null);
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
                    first.sourceLastUpdatedAt());
        }
        NetworkParticipationRecord match = matches.get(0);
        MatchMethod method = matchMethodFor(provider, match);
        return new Observation(
                NetworkEvidenceStatus.EVIDENCE_FOUND,
                method,
                match.addressLine1(),
                match.city(),
                match.stateCode(),
                match.postalCode(),
                match.sourceLastUpdatedAt());
    }

    private static MatchMethod matchMethodFor(Provider provider, NetworkParticipationRecord match) {
        if (match.postalCode() == null) {
            return MatchMethod.NPI_EXACT;
        }
        boolean postalMatches = match.postalCode().equals(provider.getPostalCode());
        boolean lineMatches = match.addressLine1() != null && match.addressLine1().equalsIgnoreCase(provider.getAddressLine1());
        boolean cityMatches = Objects.equals(match.city(), provider.getCity());
        if (postalMatches && lineMatches && cityMatches) {
            return MatchMethod.NPI_AND_LOCATION;
        }
        if (postalMatches) {
            return MatchMethod.NPI_AND_POSTAL_CODE;
        }
        return MatchMethod.NPI_EXACT;
    }

    private record Observation(
            NetworkEvidenceStatus status,
            MatchMethod matchMethod,
            String addressLine1,
            String city,
            String stateCode,
            String postalCode,
            Instant sourceLastUpdatedAt) {
    }
}
