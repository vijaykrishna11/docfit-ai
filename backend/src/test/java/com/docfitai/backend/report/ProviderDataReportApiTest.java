package com.docfitai.backend.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.docfitai.backend.testsupport.PostgresIntegrationSupport;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

/**
 * Directory-data correction reports (CLAUDE.md "Data Correction Reporting"): anonymous submission
 * allowed, validated against real provider/location data, never leaks into an automatic data
 * change, and is rate-limited.
 *
 * <p>ReportRateLimiter is a real singleton shared across every test in this class (and the whole
 * Spring test context) -- each test uses {@link #fromIp} to simulate its own distinct client IP so
 * that request budgets in one test can never accidentally starve or trip the limiter for another,
 * regardless of JUnit's (unspecified) method execution order.
 */
@AutoConfigureMockMvc
class ProviderDataReportApiTest extends PostgresIntegrationSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ProviderDataReportRepository reportRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static MockHttpServletRequestBuilder fromIp(MockHttpServletRequestBuilder builder, String ip) {
        return builder.with(request -> {
            request.setRemoteAddr(ip);
            return request;
        });
    }

    @Test
    void anonymousSubmissionIsAcceptedAndStoredAsAReviewSignal() throws Exception {
        Long providerId = insertProviderWithLocation(
                jdbcTemplate, "8500000001", "Report", "Test", "1 Test St", "Long Beach", "CA", "90802", null, 33.77, -118.19);

        String response = mockMvc.perform(fromIp(post("/api/providers/" + providerId + "/reports"), "10.10.10.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("reportType", "WRONG_PHONE_NUMBER", "comment", "The phone number listed is disconnected."))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long reportId = objectMapper.readTree(response).get("id").asLong();
        ProviderDataReport stored = reportRepository.findById(reportId).orElseThrow();
        assertThat(stored.getProviderId()).isEqualTo(providerId);
        assertThat(stored.getUserId()).isNull();
        assertThat(stored.getReportType()).isEqualTo(ReportType.WRONG_PHONE_NUMBER);
        assertThat(stored.getStatus()).isEqualTo(ReportStatus.NEW);

        // The report changed nothing about the provider's actual directory data.
        String phone = jdbcTemplate.queryForObject(
                "SELECT phone FROM provider_location WHERE provider_id = ?", String.class, providerId);
        assertThat(phone).isNull();
    }

    @Test
    void unknownProviderIsRejectedWith404() throws Exception {
        mockMvc.perform(fromIp(post("/api/providers/999999999/reports"), "10.10.10.2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reportType", "WRONG_ADDRESS"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void aLocationThatDoesNotBelongToTheProviderIsRejectedWith400() throws Exception {
        Long providerId = insertProviderWithLocation(
                jdbcTemplate, "8500000002", "Report", "Test", "1 Test St", "Long Beach", "CA", "90802", null, 33.77, -118.19);
        Long otherProviderId = insertProviderWithLocation(
                jdbcTemplate, "8500000003", "Other", "Provider", "2 Other St", "Long Beach", "CA", "90802", null, 33.78, -118.19);
        Long otherProvidersLocationId =
                jdbcTemplate.queryForObject("SELECT id FROM provider_location WHERE provider_id = ?", Long.class, otherProviderId);

        mockMvc.perform(fromIp(post("/api/providers/" + providerId + "/reports"), "10.10.10.3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("reportType", "WRONG_ADDRESS", "providerLocationId", otherProvidersLocationId))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aCommentOverTheLengthLimitIsRejectedWith400() throws Exception {
        Long providerId = insertProviderWithLocation(
                jdbcTemplate, "8500000004", "Report", "Test", "1 Test St", "Long Beach", "CA", "90802", null, 33.77, -118.19);

        // Fails @Valid at the controller boundary before the service (and its rate-limit check)
        // ever runs -- doesn't need its own IP, but given one anyway for consistency/clarity.
        mockMvc.perform(fromIp(post("/api/providers/" + providerId + "/reports"), "10.10.10.4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reportType", "OTHER", "comment", "x".repeat(1001)))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submissionIsRateLimitedByIp() throws Exception {
        Long providerId = insertProviderWithLocation(
                jdbcTemplate, "8500000005", "Report", "Test", "1 Test St", "Long Beach", "CA", "90802", null, 33.77, -118.19);
        String body = objectMapper.writeValueAsString(Map.of("reportType", "OTHER"));
        String ip = "10.10.10.5";

        // Default limit is 5 per window (docfitai.reports.rate-limit.max-attempts), exclusive to
        // this test's own simulated IP -- the 6th request must be throttled.
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(fromIp(post("/api/providers/" + providerId + "/reports"), ip)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated());
        }
        mockMvc.perform(fromIp(post("/api/providers/" + providerId + "/reports"), ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests());
    }
}
