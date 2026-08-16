package com.docfitai.backend.insurance.connector.fhir;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Operator-supplied configuration for a real Da Vinci Plan-Net FHIR source. {@code baseUrl} is
 * unset by default -- it must come from server-side configuration, never a user-supplied value
 * (SSRF rule, CLAUDE.md 80). Unset means the connector reports itself unhealthy/unconfigured
 * rather than being wired to any specific payer out of the box.
 */
@Component
@ConfigurationProperties(prefix = "docfitai.insurance.fhir-plan-net")
public class FhirPlanNetProperties {

    private String baseUrl;
    private int connectTimeoutMs = 3000;
    private int readTimeoutMs = 5000;
    private int maxRetries = 2;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public boolean isConfigured() {
        return baseUrl != null && !baseUrl.isBlank();
    }
}
