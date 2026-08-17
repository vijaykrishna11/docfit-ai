package com.docfitai.backend.provider.nppes;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Thin client for the public NPI Registry / NPPES search API (https://npiregistry.cms.hhs.gov/api/,
 * see docs/provider-source-research.md). Bounded timeouts and a small retry budget for transient
 * failures only (CLAUDE.md "Retry Policy" -- never retries a 4xx, which is a real request problem,
 * not a transient one); a short pause between attempts is a courtesy to the source, not a response
 * to any specific documented rate limit (NPPES does not publish a numeric one).
 */
@Component
public class NppesClient {

    private static final String BASE_URL = "https://npiregistry.cms.hhs.gov/api/";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration RETRY_DELAY = Duration.ofMillis(500);

    private static final Logger log = LoggerFactory.getLogger(NppesClient.class);

    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** @param enumerationType "NPI-1" (individual) or "NPI-2" (organization). */
    public NppesResponse searchByPostalCode(String postalCode, String enumerationType, int limit) {
        return searchByPostalCode(postalCode, enumerationType, limit, 0);
    }

    /**
     * @param enumerationType "NPI-1" (individual) or "NPI-2" (organization).
     * @param skip pagination offset -- verified empirically against the live API (CLAUDE.md
     *     "Investigate NPPES API Limitations"): {@code limit} is silently capped at 200 per
     *     request regardless of the value requested, but {@code skip} genuinely pages further
     *     (skip=200 returns a different result set than skip=0 for the same query).
     */
    public NppesResponse searchByPostalCode(String postalCode, String enumerationType, int limit, int skip) {
        String url = BASE_URL + "?version=2.1"
                + "&postal_code=" + URLEncoder.encode(postalCode, StandardCharsets.UTF_8)
                + "&enumeration_type=" + URLEncoder.encode(enumerationType, StandardCharsets.UTF_8)
                + "&limit=" + limit
                + "&skip=" + skip;
        HttpRequest request =
                HttpRequest.newBuilder(URI.create(url)).timeout(REQUEST_TIMEOUT).GET().build();

        IllegalStateException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return objectMapper.readValue(response.body(), NppesResponse.class);
                }
                if (response.statusCode() >= 400 && response.statusCode() < 500) {
                    // A 4xx (bad request, not-found, etc.) will not resolve itself on retry.
                    throw new IllegalStateException(
                            "NPPES API returned HTTP " + response.statusCode() + " for postal code " + postalCode);
                }
                lastFailure = new IllegalStateException(
                        "NPPES API returned HTTP " + response.statusCode() + " for postal code " + postalCode);
            } catch (IOException e) {
                lastFailure = new IllegalStateException("Failed to call NPPES API for postal code " + postalCode, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while calling NPPES API for postal code " + postalCode, e);
            }
            if (attempt < MAX_ATTEMPTS) {
                log.warn(
                        "NPPES request failed (attempt {}/{}) for postal code {}, retrying: {}",
                        attempt,
                        MAX_ATTEMPTS,
                        postalCode,
                        lastFailure.getMessage());
                sleep(RETRY_DELAY);
            }
        }
        throw lastFailure;
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
