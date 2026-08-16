package com.docfitai.backend.insurance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.docfitai.backend.insurance.dto.InsurancePlanDto;
import com.docfitai.backend.insurance.dto.NetworkEvidenceDetailDto;
import com.docfitai.backend.insurance.dto.PayerDto;
import com.docfitai.backend.insurance.evidence.NetworkEvidenceImportService;
import com.docfitai.backend.insurance.evidence.ProviderNetworkEvidenceRepository;
import com.docfitai.backend.insurance.connector.NetworkParticipationRecord;
import com.docfitai.backend.provider.Provider;
import com.docfitai.backend.provider.ProviderRepository;
import com.docfitai.backend.provider.dto.ProviderSearchResponseDto;
import com.docfitai.backend.testsupport.PostgresIntegrationSupport;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
class InsuranceApiTest extends PostgresIntegrationSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private InsuranceNetworkRepository insuranceNetworkRepository;

    @Autowired
    private InsurancePlanRepository insurancePlanRepository;

    @Autowired
    private NetworkSourceRepository networkSourceRepository;

    @Autowired
    private ProviderNetworkEvidenceRepository evidenceRepository;

    @Autowired
    private NetworkEvidenceImportService importService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void payerListIncludesLegacyCarriersAndTheDemoPayerWithIntegratedPlans() throws Exception {
        String body = mockMvc.perform(get("/api/insurance/payers"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<PayerDto> payers = List.of(objectMapper.readValue(body, PayerDto[].class));

        assertThat(payers).anySatisfy(p -> assertThat(p.name()).contains("Aetna"));
        assertThat(payers).anySatisfy(p -> {
            assertThat(p.name()).contains("DocFit Demo Network");
            assertThat(p.hasIntegratedPlans()).isTrue();
        });
        // A known-but-unintegrated payer must not silently claim integration.
        assertThat(payers).filteredOn(p -> p.name().contains("Aetna")).allSatisfy(p -> assertThat(p.hasIntegratedPlans()).isFalse());
    }

    @Test
    void plansEndpointReturnsPlansForIntegratedPayerOnly() throws Exception {
        Long demoPayerId = jdbcTemplate.queryForObject("SELECT id FROM payer WHERE code = 'DOCFIT_DEMO'", Long.class);

        String body = mockMvc.perform(get("/api/insurance/payers/" + demoPayerId + "/plans"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<InsurancePlanDto> plans = List.of(objectMapper.readValue(body, InsurancePlanDto[].class));

        assertThat(plans).hasSize(1);
        assertThat(plans.get(0).planName()).contains("synthetic");
    }

    @Test
    void searchWithPlanIdAttachesNetworkEvidenceWithoutBreakingResultsWhenEvidenceMissing() throws Exception {
        String npi = "5000000001";
        jdbcTemplate.update(
                "INSERT INTO provider (npi_number, first_name, last_name, address_line_1, city, state_code, postal_code, latitude, longitude) "
                        + "VALUES (?, 'Search', 'EvidenceDoctor', '200 Ocean Blvd', 'Long Beach', 'CA', '90802', 33.770000, -118.191000)",
                npi);
        Long providerId = jdbcTemplate.queryForObject("SELECT id FROM provider WHERE npi_number = ?", Long.class, npi);
        jdbcTemplate.update(
                "INSERT INTO provider_taxonomy (provider_id, taxonomy_code, primary_taxonomy) VALUES (?, '207RC0000X', true)",
                providerId);
        Long planId = jdbcTemplate.queryForObject(
                "SELECT ip.id FROM insurance_plan ip JOIN payer p ON p.id = ip.payer_id WHERE p.code = 'DOCFIT_DEMO'", Long.class);

        String body = mockMvc.perform(get("/api/providers/search")
                        .param("specialty", "CARDIOLOGY")
                        .param("zip", "90802")
                        .param("planId", String.valueOf(planId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        ProviderSearchResponseDto response = objectMapper.readValue(body, ProviderSearchResponseDto.class);

        assertThat(response.results()).anySatisfy(r -> {
            if (npi.equals(r.npiNumber())) {
                assertThat(r.networkEvidence()).isNotNull();
                // Never checked yet for this provider -- must not be silently omitted or fabricated as found.
                assertThat(r.networkEvidence().status()).isEqualTo("NOT_CHECKED");
            }
        });
    }

    @Test
    void networkEvidenceDetailEndpointReturnsSourceAndMatchMethodWhenFound() throws Exception {
        String npi = "5000000002";
        jdbcTemplate.update(
                "INSERT INTO provider (npi_number, first_name, last_name, address_line_1, city, state_code, postal_code) "
                        + "VALUES (?, 'Detail', 'EvidenceDoctor', '1 Test Ave', 'Long Beach', 'CA', '90802')",
                npi);
        Provider provider = providerRepository.findByNpiNumber(npi).orElseThrow();
        Long payerId = jdbcTemplate.queryForObject("SELECT id FROM payer WHERE code = 'DOCFIT_DEMO'", Long.class);
        var network = insuranceNetworkRepository.findByPayerIdAndExternalNetworkIdentifier(payerId, "DEMO-NETWORK-1").orElseThrow();
        var plan = insurancePlanRepository.findByPayerIdAndExternalPlanIdentifier(payerId, "DEMO-PLAN-1").orElseThrow();
        var source = networkSourceRepository.findByPayerId(payerId).get(0);
        Instant now = Instant.now();
        importService.recordObservation(
                provider, network, plan, source,
                List.of(new NetworkParticipationRecord(npi, "DEMO-NETWORK-1", "DEMO-PLAN-1", "1 Test Ave", "Long Beach", "CA", "90802", now)),
                now);

        String body = mockMvc.perform(get("/api/providers/" + provider.getId() + "/network-evidence").param("planId", String.valueOf(plan.getId())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        NetworkEvidenceDetailDto detail = objectMapper.readValue(body, NetworkEvidenceDetailDto.class);

        assertThat(detail.status()).isEqualTo("EVIDENCE_FOUND");
        assertThat(detail.matchMethod()).isEqualTo("NPI_AND_LOCATION");
        assertThat(detail.sourceName()).isNotBlank();
        assertThat(detail.synthetic()).isTrue();
        assertThat(detail.limitations()).anySatisfy(text -> assertThat(text).containsIgnoringCase("does not guarantee"));
    }

    @Test
    void networkEvidenceEndpointReturns404ForUnknownProvider() throws Exception {
        Long planId = jdbcTemplate.queryForObject(
                "SELECT ip.id FROM insurance_plan ip JOIN payer p ON p.id = ip.payer_id WHERE p.code = 'DOCFIT_DEMO'", Long.class);
        mockMvc.perform(get("/api/providers/999999999/network-evidence").param("planId", String.valueOf(planId)))
                .andExpect(status().isNotFound());
    }
}
