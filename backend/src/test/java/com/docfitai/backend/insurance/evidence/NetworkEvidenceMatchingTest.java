package com.docfitai.backend.insurance.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import com.docfitai.backend.insurance.InsuranceNetwork;
import com.docfitai.backend.insurance.InsuranceNetworkRepository;
import com.docfitai.backend.insurance.InsurancePlan;
import com.docfitai.backend.insurance.InsurancePlanRepository;
import com.docfitai.backend.insurance.NetworkSource;
import com.docfitai.backend.insurance.NetworkSourceRepository;
import com.docfitai.backend.insurance.connector.NetworkParticipationRecord;
import com.docfitai.backend.provider.Provider;
import com.docfitai.backend.provider.ProviderLocation;
import com.docfitai.backend.provider.ProviderLocationRepository;
import com.docfitai.backend.provider.ProviderRepository;
import com.docfitai.backend.testsupport.PostgresIntegrationSupport;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Explainability/match-method coverage (CLAUDE.md 65-66): exact NPI, NPI+location, NPI+postal
 * only, ambiguous, no-evidence, and re-import dedup -- against a real PostgreSQL Testcontainer,
 * matching this module's existing "no H2 shortcuts" convention.
 */
class NetworkEvidenceMatchingTest extends PostgresIntegrationSupport {

    private static final String NPI = "6000000001";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private ProviderLocationRepository providerLocationRepository;

    @Autowired
    private NetworkSourceRepository networkSourceRepository;

    @Autowired
    private InsuranceNetworkRepository insuranceNetworkRepository;

    @Autowired
    private InsurancePlanRepository insurancePlanRepository;

    @Autowired
    private ProviderNetworkEvidenceRepository evidenceRepository;

    @Autowired
    private NetworkEvidenceImportService importService;

    @Test
    void matchMethodsAreClassifiedExplainably() {
        Provider provider = insertProvider(NPI, "200 Ocean Blvd", "Long Beach", "CA", "90802");
        List<ProviderLocation> locations = providerLocationRepository.findByProviderIdOrderByPrimaryDescId(provider.getId());
        InsuranceNetwork network = insuranceNetworkRepository
                .findByPayerIdAndExternalNetworkIdentifier(payerId(), "DEMO-NETWORK-1")
                .orElseThrow();
        InsurancePlan plan = insurancePlanRepository
                .findByPayerIdAndExternalPlanIdentifier(payerId(), "DEMO-PLAN-1")
                .orElseThrow();
        NetworkSource source = networkSourceRepository.findByPayerId(payerId()).get(0);
        Instant now = Instant.now();

        // Exact location match.
        ProviderNetworkEvidence located = importService.recordObservation(
                provider,
                locations,
                network,
                plan,
                source,
                List.of(new NetworkParticipationRecord(NPI, "DEMO-NETWORK-1", "DEMO-PLAN-1", "200 Ocean Blvd", "Long Beach", "CA", "90802", now)),
                now);
        assertThat(located.getStatus()).isEqualTo(NetworkEvidenceStatus.EVIDENCE_FOUND);
        assertThat(located.getMatchMethod()).isEqualTo(MatchMethod.NPI_AND_LOCATION);
        assertThat(located.getProviderLocation()).isNotNull();
        assertThat(located.getProviderLocation().getId()).isEqualTo(locations.get(0).getId());

        // Postal-only match (different street line).
        ProviderNetworkEvidence postalOnly = importService.recordObservation(
                provider,
                locations,
                network,
                plan,
                source,
                List.of(new NetworkParticipationRecord(NPI, "DEMO-NETWORK-1", "DEMO-PLAN-1", "Suite 900", "Long Beach", "CA", "90802", now)),
                now);
        assertThat(postalOnly.getMatchMethod()).isEqualTo(MatchMethod.NPI_AND_POSTAL_CODE);

        // No location reported by source at all.
        ProviderNetworkEvidence noLocation = importService.recordObservation(
                provider, locations, network, plan, source,
                List.of(new NetworkParticipationRecord(NPI, "DEMO-NETWORK-1", "DEMO-PLAN-1", null, null, null, null, now)), now);
        assertThat(noLocation.getMatchMethod()).isEqualTo(MatchMethod.NPI_EXACT);
        assertThat(noLocation.getProviderLocation()).isNull();

        // Two conflicting records -> ambiguous, never silently picks one as truth.
        ProviderNetworkEvidence ambiguous = importService.recordObservation(
                provider,
                locations,
                network,
                plan,
                source,
                List.of(
                        new NetworkParticipationRecord(NPI, "DEMO-NETWORK-1", "DEMO-PLAN-1", "1 First St", "Long Beach", "CA", "90802", now),
                        new NetworkParticipationRecord(NPI, "DEMO-NETWORK-1", "DEMO-PLAN-1", "2 Second St", "Long Beach", "CA", "90803", now)),
                now);
        assertThat(ambiguous.getStatus()).isEqualTo(NetworkEvidenceStatus.MATCH_AMBIGUOUS);
        assertThat(ambiguous.getMatchMethod()).isEqualTo(MatchMethod.AMBIGUOUS);

        // Checked, found nothing -- never conflated with "out of network".
        ProviderNetworkEvidence noEvidence =
                importService.recordObservation(provider, locations, network, plan, source, List.of(), now);
        assertThat(noEvidence.getStatus()).isEqualTo(NetworkEvidenceStatus.NO_EVIDENCE_FOUND);
    }

    @Test
    void reimportingTheSameObservationUpdatesRatherThanDuplicates() {
        Provider provider = insertProvider("6000000002", "1 Test Ave", "Long Beach", "CA", "90802");
        List<ProviderLocation> locations = providerLocationRepository.findByProviderIdOrderByPrimaryDescId(provider.getId());
        InsuranceNetwork network = insuranceNetworkRepository
                .findByPayerIdAndExternalNetworkIdentifier(payerId(), "DEMO-NETWORK-1")
                .orElseThrow();
        InsurancePlan plan = insurancePlanRepository
                .findByPayerIdAndExternalPlanIdentifier(payerId(), "DEMO-PLAN-1")
                .orElseThrow();
        NetworkSource source = networkSourceRepository.findByPayerId(payerId()).get(0);
        Instant firstCheck = Instant.now().minus(10, ChronoUnit.DAYS);
        Instant secondCheck = Instant.now();

        importService.recordObservation(
                provider, locations, network, plan, source,
                List.of(new NetworkParticipationRecord("6000000002", "DEMO-NETWORK-1", "DEMO-PLAN-1", null, null, null, null, firstCheck)),
                firstCheck);
        importService.recordObservation(
                provider, locations, network, plan, source,
                List.of(new NetworkParticipationRecord("6000000002", "DEMO-NETWORK-1", "DEMO-PLAN-1", null, null, null, null, secondCheck)),
                secondCheck);

        long rows = evidenceRepository.findByProviderIdsAndPlanId(List.of(provider.getId()), plan.getId()).size();
        assertThat(rows).isEqualTo(1);
    }

    private Provider insertProvider(String npi, String line1, String city, String state, String postal) {
        insertProviderWithLocation(jdbcTemplate, npi, "Evidence", "TestDoctor", line1, city, state, postal, null, null, null);
        return providerRepository.findByNpiNumber(npi).orElseThrow();
    }

    private Long payerId() {
        return jdbcTemplate.queryForObject("SELECT id FROM payer WHERE code = 'DOCFIT_DEMO'", Long.class);
    }
}
