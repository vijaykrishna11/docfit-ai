package com.docfitai.backend.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.docfitai.backend.insurance.InsuranceNetwork;
import com.docfitai.backend.insurance.InsuranceNetworkRepository;
import com.docfitai.backend.insurance.InsurancePlan;
import com.docfitai.backend.insurance.InsurancePlanRepository;
import com.docfitai.backend.insurance.NetworkSource;
import com.docfitai.backend.insurance.NetworkSourceRepository;
import com.docfitai.backend.insurance.connector.NetworkParticipationRecord;
import com.docfitai.backend.insurance.evidence.NetworkEvidenceImportService;
import com.docfitai.backend.provider.dto.ProviderSearchResponseDto;
import com.docfitai.backend.provider.dto.ProviderSearchResultDto;
import com.docfitai.backend.testsupport.PostgresIntegrationSupport;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * Practical-fit filters (CLAUDE.md "Practical Fit Filter Bar"): providerType, hasPhone,
 * preciseLocationOnly, multipleLocations, networkEvidenceFound. Each filter is applied against a
 * shared fixture (one specialty, several providers each varying exactly one practical-fit
 * attribute) so a passing test proves the filter narrows correctly without accidentally excluding
 * providers that shouldn't be affected by it.
 */
class ProviderSearchPracticalFiltersTest extends PostgresIntegrationSupport {

    @Autowired
    private ProviderSearchService providerSearchService;

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private NetworkEvidenceImportService importService;

    @Autowired
    private InsuranceNetworkRepository insuranceNetworkRepository;

    @Autowired
    private InsurancePlanRepository insurancePlanRepository;

    @Autowired
    private NetworkSourceRepository networkSourceRepository;

    @Autowired
    private ProviderLocationRepository providerLocationRepository;

    /**
     * Every test gets its own NPI block (this suite shares one database across many test classes
     * and methods with no per-test rollback, matching this module's established convention) so
     * fixtures from one filter test can never collide with or pollute another's.
     */
    private record Fixture(
            String individualWithPhone, String individualNoPhone, String organizationWithPhone, String impreciseLocation, String multiLocation) {
    }

    private Fixture seedBaseFixture(String block) {
        String individualWithPhone = block + "1";
        String individualNoPhone = block + "2";
        String organizationWithPhone = block + "3";
        String impreciseLocation = block + "4";
        String multiLocation = block + "5";
        insertIndividual(individualWithPhone, "90802", "562-555-0001", 33.770000, -118.191000, "EXACT");
        insertIndividual(individualNoPhone, "90802", null, 33.771000, -118.190000, "EXACT");
        insertOrganization(organizationWithPhone, "90802", "562-555-0003", 33.772000, -118.192000, "EXACT");
        insertIndividual(impreciseLocation, "90802", "562-555-0004", 33.773000, -118.193000, "ZIP_CENTROID");
        Long multiLocationId = insertIndividual(multiLocation, "90802", "562-555-0005", 33.774000, -118.194000, "EXACT");
        insertLocation(multiLocationId, "2 Second Office", "90815", "562-555-0006", 33.794000, -118.116000, "EXACT", false);
        return new Fixture(individualWithPhone, individualNoPhone, organizationWithPhone, impreciseLocation, multiLocation);
    }

    @Test
    void providerTypeFilterNarrowsToTheRequestedEntityType() {
        Fixture fixture = seedBaseFixture("820000000");
        ProviderSearchResponseDto organizationsOnly = providerSearchService.search(query(q -> q.providerType = "ORGANIZATION"));
        assertThat(organizationsOnly.results()).extracting(ProviderSearchResultDto::npiNumber).contains(fixture.organizationWithPhone());
        assertThat(organizationsOnly.results())
                .extracting(ProviderSearchResultDto::npiNumber)
                .doesNotContain(fixture.individualWithPhone(), fixture.individualNoPhone(), fixture.multiLocation());

        ProviderSearchResponseDto individualsOnly = providerSearchService.search(query(q -> q.providerType = "INDIVIDUAL"));
        assertThat(individualsOnly.results())
                .extracting(ProviderSearchResultDto::npiNumber)
                .doesNotContain(fixture.organizationWithPhone());
    }

    @Test
    void hasPhoneFilterExcludesLocationsWithoutAPhoneNumber() {
        Fixture fixture = seedBaseFixture("821000000");
        ProviderSearchResponseDto response = providerSearchService.search(query(q -> q.hasPhone = true));
        assertThat(response.results()).extracting(ProviderSearchResultDto::npiNumber).doesNotContain(fixture.individualNoPhone());
        assertThat(response.results()).extracting(ProviderSearchResultDto::npiNumber).contains(fixture.individualWithPhone());
    }

    @Test
    void preciseLocationOnlyFilterExcludesZipCentroidApproximations() {
        Fixture fixture = seedBaseFixture("822000000");
        ProviderSearchResponseDto response = providerSearchService.search(query(q -> q.preciseLocationOnly = true));
        assertThat(response.results()).extracting(ProviderSearchResultDto::npiNumber).doesNotContain(fixture.impreciseLocation());
        assertThat(response.results()).extracting(ProviderSearchResultDto::npiNumber).contains(fixture.individualWithPhone());
    }

    @Test
    void multipleLocationsFilterOnlyReturnsProvidersWithMoreThanOneLocation() {
        Fixture fixture = seedBaseFixture("823000000");
        ProviderSearchResponseDto response = providerSearchService.search(query(q -> q.multipleLocations = true));
        assertThat(response.results()).extracting(ProviderSearchResultDto::npiNumber).contains(fixture.multiLocation());
        assertThat(response.results())
                .extracting(ProviderSearchResultDto::npiNumber)
                .doesNotContain(fixture.individualWithPhone(), fixture.individualNoPhone(), fixture.organizationWithPhone());
    }

    @Test
    void networkEvidenceFoundFilterRequiresAPlanIdOr400() {
        seedBaseFixture("824000000");
        assertThatThrownBy(() -> providerSearchService.search(query(q -> q.networkEvidenceFound = true)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
                .isEqualTo(400);
    }

    @Test
    void networkEvidenceFoundFilterNarrowsToProvidersWithEvidenceAtTheShownLocation() {
        String evidenceFoundNpi = "8250000001";
        String noEvidenceNpi = "8250000002";
        insertIndividual(evidenceFoundNpi, "90802", "562-555-0007", 33.775000, -118.195000, "EXACT");
        insertIndividual(noEvidenceNpi, "90802", "562-555-0008", 33.776000, -118.196000, "EXACT");

        Long payerId = jdbcTemplate.queryForObject("SELECT id FROM payer WHERE code = 'DOCFIT_DEMO'", Long.class);
        InsuranceNetwork network =
                insuranceNetworkRepository.findByPayerIdAndExternalNetworkIdentifier(payerId, "DEMO-NETWORK-1").orElseThrow();
        InsurancePlan plan = insurancePlanRepository.findByPayerIdAndExternalPlanIdentifier(payerId, "DEMO-PLAN-1").orElseThrow();
        NetworkSource source = networkSourceRepository.findByPayerId(payerId).get(0);
        Provider evidenceProvider = providerRepository.findByNpiNumber(evidenceFoundNpi).orElseThrow();
        List<ProviderLocation> evidenceProviderLocations =
                providerLocationRepository.findByProviderIdOrderByPrimaryDescId(evidenceProvider.getId());
        Instant now = Instant.now();
        importService.recordObservation(
                evidenceProvider,
                evidenceProviderLocations,
                network,
                plan,
                source,
                List.of(new NetworkParticipationRecord(
                        evidenceFoundNpi, "DEMO-NETWORK-1", "DEMO-PLAN-1", null, null, null, null, now)),
                now);

        ProviderSearchResponseDto response = providerSearchService.search(
                query(q -> {
                    q.networkEvidenceFound = true;
                    q.planId = plan.getId();
                }));

        assertThat(response.results()).extracting(ProviderSearchResultDto::npiNumber).contains(evidenceFoundNpi);
        assertThat(response.results()).extracting(ProviderSearchResultDto::npiNumber).doesNotContain(noEvidenceNpi);
        ProviderSearchResultDto matched =
                response.results().stream().filter(r -> evidenceFoundNpi.equals(r.npiNumber())).findFirst().orElseThrow();
        assertThat(matched.networkEvidence()).isNotNull();
        assertThat(matched.networkEvidence().status()).isEqualTo("EVIDENCE_FOUND");
    }

    private interface QueryCustomizer {
        void customize(MutableQuery q);
    }

    private static final class MutableQuery {
        String providerType;
        Boolean hasPhone;
        Boolean preciseLocationOnly;
        Boolean networkEvidenceFound;
        Boolean multipleLocations;
        Long planId;
    }

    private ProviderSearchQuery query(QueryCustomizer customizer) {
        MutableQuery q = new MutableQuery();
        customizer.customize(q);
        return new ProviderSearchQuery(
                "CARDIOLOGY", "90802", null, null, null, 50, "distance", 0, 200, q.planId, q.providerType, q.hasPhone,
                q.preciseLocationOnly, q.networkEvidenceFound, q.multipleLocations);
    }

    private Long insertIndividual(
            String npi, String postal, String phone, double lat, double lng, String coordinatePrecision) {
        jdbcTemplate.update(
                "INSERT INTO provider (npi_number, entity_type, first_name, last_name) VALUES (?, 'INDIVIDUAL', 'Filter', 'Test')",
                npi);
        Long providerId = providerRepository.findByNpiNumber(npi).orElseThrow().getId();
        insertLocation(providerId, "1 Test St", postal, phone, lat, lng, coordinatePrecision, true);
        insertTaxonomy(providerId);
        return providerId;
    }

    private void insertOrganization(String npi, String postal, String phone, double lat, double lng, String coordinatePrecision) {
        jdbcTemplate.update(
                "INSERT INTO provider (npi_number, entity_type, organization_name) VALUES (?, 'ORGANIZATION', 'Filter Test Clinic')",
                npi);
        Long providerId = providerRepository.findByNpiNumber(npi).orElseThrow().getId();
        insertLocation(providerId, "1 Test St", postal, phone, lat, lng, coordinatePrecision, true);
        insertTaxonomy(providerId);
    }

    private void insertLocation(
            Long providerId, String line1, String postal, String phone, double lat, double lng, String coordinatePrecision, boolean primary) {
        jdbcTemplate.update(
                "INSERT INTO provider_location (provider_id, address_purpose, address_line_1, city, state_code, "
                        + "postal_code, phone, latitude, longitude, coordinate_precision, is_primary, normalized_key) "
                        + "VALUES (?, 'LOCATION', ?, 'Long Beach', 'CA', ?, ?, ?, ?, ?, ?, ?)",
                providerId, line1, postal, phone, lat, lng, coordinatePrecision, primary, providerId + "|" + line1);
    }

    private void insertTaxonomy(Long providerId) {
        jdbcTemplate.update(
                "INSERT INTO provider_taxonomy (provider_id, taxonomy_code, primary_taxonomy) VALUES (?, '207RC0000X', true)",
                providerId);
    }
}
