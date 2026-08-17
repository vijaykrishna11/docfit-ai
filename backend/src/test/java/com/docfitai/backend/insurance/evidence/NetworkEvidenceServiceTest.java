package com.docfitai.backend.insurance.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import com.docfitai.backend.insurance.InsuranceNetwork;
import com.docfitai.backend.insurance.InsuranceNetworkRepository;
import com.docfitai.backend.insurance.InsurancePlan;
import com.docfitai.backend.insurance.InsurancePlanRepository;
import com.docfitai.backend.insurance.NetworkSource;
import com.docfitai.backend.insurance.NetworkSourceRepository;
import com.docfitai.backend.insurance.connector.NetworkParticipationRecord;
import com.docfitai.backend.insurance.dto.NetworkEvidenceDetailDto;
import com.docfitai.backend.provider.Provider;
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
 * Explainability tests (CLAUDE.md 65): EVIDENCE_FOUND shows a source, NO_EVIDENCE_FOUND never
 * renders as "out of network" (enforced at the enum level -- no such value exists), a disabled
 * source degrades to SOURCE_UNAVAILABLE without breaking the lookup, and freshness is derived
 * from checked_at rather than stored.
 */
class NetworkEvidenceServiceTest extends PostgresIntegrationSupport {

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
    private NetworkEvidenceImportService importService;

    @Autowired
    private NetworkEvidenceService networkEvidenceService;

    @Test
    void statusEnumNeverImpliesOutOfNetwork() {
        assertThat(NetworkEvidenceStatus.values())
                .extracting(Enum::name)
                .noneMatch(name -> name.contains("OUT_OF_NETWORK") || name.equals("COVERED") || name.equals("NOT_COVERED"));
    }

    @Test
    void freshnessIsDerivedFromCheckedAtNotStored() {
        Provider provider = insertProvider("6100000001");
        var locations = providerLocationRepository.findByProviderIdOrderByPrimaryDescId(provider.getId());
        InsuranceNetwork network = network();
        InsurancePlan plan = plan();
        NetworkSource source = source();

        Instant freshCheck = Instant.now().minus(5, ChronoUnit.DAYS);
        importService.recordObservation(
                provider, locations, network, plan, source,
                List.of(new NetworkParticipationRecord(provider.getNpiNumber(), "DEMO-NETWORK-1", "DEMO-PLAN-1", null, null, null, null, freshCheck)),
                freshCheck);

        NetworkEvidenceDetailDto detail = networkEvidenceService.lookup(provider.getId(), null, plan);
        assertThat(detail.status()).isEqualTo("EVIDENCE_FOUND");
        assertThat(detail.freshness()).isEqualTo("FRESH");
        assertThat(detail.sourceName()).isNotBlank();
        assertThat(detail.matchMethod()).isEqualTo("NPI_EXACT");

        Instant staleCheck = Instant.now().minus(90, ChronoUnit.DAYS);
        importService.recordObservation(
                provider, locations, network, plan, source,
                List.of(new NetworkParticipationRecord(provider.getNpiNumber(), "DEMO-NETWORK-1", "DEMO-PLAN-1", null, null, null, null, staleCheck)),
                staleCheck);

        NetworkEvidenceDetailDto staleDetail = networkEvidenceService.lookup(provider.getId(), null, plan);
        assertThat(staleDetail.freshness()).isEqualTo("STALE");
    }

    @Test
    void neverCheckedProviderReturnsNotCheckedNotAnError() {
        Provider provider = insertProvider("6100000002");
        InsurancePlan plan = plan();

        NetworkEvidenceDetailDto detail = networkEvidenceService.lookup(provider.getId(), null, plan);

        assertThat(detail.status()).isEqualTo("NOT_CHECKED");
        assertThat(detail.limitations()).isNotEmpty();
    }

    @Test
    void disabledSourceDegradesToSourceUnavailableWithoutBreakingLookup() {
        Provider provider = insertProvider("6100000003");
        var locations = providerLocationRepository.findByProviderIdOrderByPrimaryDescId(provider.getId());
        InsuranceNetwork network = network();
        InsurancePlan plan = plan();
        NetworkSource source = source();
        Instant now = Instant.now();
        importService.recordObservation(
                provider, locations, network, plan, source,
                List.of(new NetworkParticipationRecord(provider.getNpiNumber(), "DEMO-NETWORK-1", "DEMO-PLAN-1", null, null, null, null, now)),
                now);

        jdbcTemplate.update("UPDATE network_source SET active = false WHERE id = ?", source.getId());

        NetworkEvidenceDetailDto detail = networkEvidenceService.lookup(provider.getId(), null, plan);
        assertThat(detail.status()).isEqualTo("SOURCE_UNAVAILABLE");

        jdbcTemplate.update("UPDATE network_source SET active = true WHERE id = ?", source.getId());
    }

    @Test
    void evidenceBoundToOneLocationNeverLeaksToTheProvidersOtherLocation() {
        // A provider with two distinct offices. Evidence is recorded with an address that matches
        // only Office A. Looking up Office B must never show Office A's evidence as if it applied
        // there too (CLAUDE.md 9) -- with no provider-wide (location-less) evidence to fall back
        // to either, Office B must come back NOT_CHECKED.
        Long providerId = insertProviderWithTwoLocations("6100000004");
        Provider provider = providerRepository.findById(providerId).orElseThrow();
        var locations = providerLocationRepository.findByProviderIdOrderByPrimaryDescId(providerId);
        assertThat(locations).hasSize(2);
        var officeA = locations.stream().filter(l -> "90802".equals(l.getPostalCode())).findFirst().orElseThrow();
        var officeB = locations.stream().filter(l -> "10001".equals(l.getPostalCode())).findFirst().orElseThrow();

        InsuranceNetwork network = network();
        InsurancePlan plan = plan();
        NetworkSource source = source();
        Instant now = Instant.now();
        importService.recordObservation(
                provider,
                locations,
                network,
                plan,
                source,
                List.of(new NetworkParticipationRecord(
                        provider.getNpiNumber(), "DEMO-NETWORK-1", "DEMO-PLAN-1", "1 Office A Way", "Long Beach", "CA", "90802", now)),
                now);

        NetworkEvidenceDetailDto atOfficeA = networkEvidenceService.lookup(providerId, officeA.getId(), plan);
        assertThat(atOfficeA.status()).isEqualTo("EVIDENCE_FOUND");

        NetworkEvidenceDetailDto atOfficeB = networkEvidenceService.lookup(providerId, officeB.getId(), plan);
        assertThat(atOfficeB.status()).isEqualTo("NOT_CHECKED");
    }

    private Provider insertProvider(String npi) {
        insertProviderWithLocation(jdbcTemplate, npi, "Fresh", "TestDoctor", "1 Test Ave", "Long Beach", "CA", "90802", null, null, null);
        return providerRepository.findByNpiNumber(npi).orElseThrow();
    }

    private Long insertProviderWithTwoLocations(String npi) {
        jdbcTemplate.update(
                "INSERT INTO provider (npi_number, entity_type, first_name, last_name) VALUES (?, 'INDIVIDUAL', 'TwoOffice', 'Doctor')",
                npi);
        Long providerId = providerRepository.findByNpiNumber(npi).orElseThrow().getId();
        jdbcTemplate.update(
                "INSERT INTO provider_location (provider_id, address_purpose, address_line_1, city, state_code, "
                        + "postal_code, phone, latitude, longitude, coordinate_precision, is_primary, normalized_key) "
                        + "VALUES (?, 'LOCATION', '1 Office A Way', 'Long Beach', 'CA', '90802', null, 33.77, -118.19, 'ZIP_CENTROID', TRUE, ?)",
                providerId, npi + "|A");
        jdbcTemplate.update(
                "INSERT INTO provider_location (provider_id, address_purpose, address_line_1, city, state_code, "
                        + "postal_code, phone, latitude, longitude, coordinate_precision, is_primary, normalized_key) "
                        + "VALUES (?, 'LOCATION', '1 Office B Way', 'New York', 'NY', '10001', null, 40.75, -73.99, 'ZIP_CENTROID', FALSE, ?)",
                providerId, npi + "|B");
        return providerId;
    }

    private Long payerId() {
        return jdbcTemplate.queryForObject("SELECT id FROM payer WHERE code = 'DOCFIT_DEMO'", Long.class);
    }

    private InsuranceNetwork network() {
        return insuranceNetworkRepository.findByPayerIdAndExternalNetworkIdentifier(payerId(), "DEMO-NETWORK-1").orElseThrow();
    }

    private InsurancePlan plan() {
        return insurancePlanRepository.findByPayerIdAndExternalPlanIdentifier(payerId(), "DEMO-PLAN-1").orElseThrow();
    }

    private NetworkSource source() {
        return networkSourceRepository.findByPayerId(payerId()).get(0);
    }
}
