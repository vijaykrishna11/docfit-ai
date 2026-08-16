package com.docfitai.backend.insurance.connector.fhir;

import com.docfitai.backend.insurance.connector.ConnectorHealth;
import com.docfitai.backend.insurance.connector.DiscoveredPlan;
import com.docfitai.backend.insurance.connector.NetworkParticipationRecord;
import com.docfitai.backend.insurance.connector.ProviderNetworkConnector;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

/**
 * Real Da Vinci PDex Plan-Net FHIR R4 connector. Only activates when
 * {@code docfitai.insurance.fhir-plan-net.base-url} is set by the operator; this is not bound
 * to any specific payer by default -- see docs/insurance-network-research.md ("Connector reality
 * check") for why no live third-party endpoint is wired in as a default dependency.
 */
@Component
public class FhirPlanNetConnector implements ProviderNetworkConnector {

    private static final Logger log = LoggerFactory.getLogger(FhirPlanNetConnector.class);
    public static final String SOURCE_CODE = "FHIR_PLAN_NET";

    private final FhirPlanNetProperties properties;
    private final FhirPlanNetParser parser;
    private final RestClient restClient;

    public FhirPlanNetConnector(FhirPlanNetProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.parser = new FhirPlanNetParser(objectMapper);
        this.restClient = buildRestClient(properties);
    }

    private static RestClient buildRestClient(FhirPlanNetProperties properties) {
        if (!properties.isConfigured()) {
            return null;
        }
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()));
        requestFactory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .defaultHeader("Accept", "application/fhir+json")
                .build();
    }

    @Override
    public String sourceCode() {
        return SOURCE_CODE;
    }

    @Override
    public List<DiscoveredPlan> discoverPlans() {
        if (restClient == null) {
            return List.of();
        }
        String body = getWithRetry("/InsurancePlan?_count=50");
        return body == null ? List.of() : parser.parseInsurancePlanBundle(body);
    }

    @Override
    public List<NetworkParticipationRecord> fetchProviderNetworkParticipation(String npi) {
        if (restClient == null) {
            return List.of();
        }
        String identifier = URLEncoder.encode("http://hl7.org/fhir/sid/us-npi|" + npi, StandardCharsets.UTF_8);
        String body = getWithRetry("/PractitionerRole?practitioner.identifier=" + identifier + "&_include=PractitionerRole:location");
        return body == null ? List.of() : parser.parsePractitionerRoleBundle(body, npi);
    }

    @Override
    public ConnectorHealth healthCheck() {
        if (restClient == null) {
            return new ConnectorHealth(false, "No base URL configured (docfitai.insurance.fhir-plan-net.base-url)");
        }
        String body = getWithRetry("/metadata");
        return body == null
                ? new ConnectorHealth(false, "Source did not respond to a capability-statement check")
                : new ConnectorHealth(true, "Reachable");
    }

    /** Bounded retry: only on connection failure or 429/5xx -- never on 4xx client errors (CLAUDE.md 46). */
    private String getWithRetry(String path) {
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                return restClient.get().uri(path).retrieve().body(String.class);
            } catch (RestClientResponseException e) {
                HttpStatusCode status = e.getStatusCode();
                boolean retryable = status.value() == 429 || status.is5xxServerError();
                if (!retryable || attempt > properties.getMaxRetries()) {
                    log.warn("FHIR Plan-Net source returned {} for {} (attempt {})", status, path, attempt);
                    return null;
                }
                sleepBackoff(attempt);
            } catch (RestClientException e) {
                if (attempt > properties.getMaxRetries()) {
                    log.warn("FHIR Plan-Net source unreachable for {}: {}", path, e.getMessage());
                    return null;
                }
                sleepBackoff(attempt);
            }
        }
    }

    private static void sleepBackoff(int attempt) {
        try {
            Thread.sleep(Duration.ofMillis(200L * attempt));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
