package com.docfitai.backend.provider.geocoding;

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

/**
 * Thin client for the U.S. Census Bureau Geocoder's structured (street/city/state/zip) single-
 * address endpoint (CLAUDE.md "Census Geocoder Research"). Confirmed empirically this phase via
 * direct requests against the live public API (WebFetch could not render the official docs pages,
 * same issue as the earlier NPPES research):
 *
 * <ul>
 *   <li>{@code https://geocoding.geo.census.gov/geocoder/locations/address} accepts
 *       {@code street}/{@code city}/{@code state}/{@code zip}/{@code benchmark}/{@code format}
 *       query params, no API key required.
 *   <li>A genuine no-match returns HTTP 200 with an empty {@code addressMatches} array -- not an
 *       error (e.g. a gibberish street value against a real city/state/zip still returns 200 with
 *       zero matches).
 *   <li>A missing required parameter (e.g. no {@code street}) returns HTTP 400.
 *   <li>{@code benchmark=Public_AR_Current} is the Bureau's own current default/recommended
 *       benchmark (confirmed via {@code /geocoder/benchmarks}) -- used here rather than a pinned
 *       historical vintage.
 *   <li>No documented numeric rate limit was found (same finding as NPPES) -- this client's own
 *       bounded timeout/retry is a courtesy to the source, not a response to a specific limit.
 * </ul>
 *
 * <p>Never called from the request path ({@code GET /api/providers/search}) -- ingestion/
 * maintenance only, via {@link ProviderGeocodingService}.
 */
@Component
public class CensusGeocoderClient {

    private static final String BASE_URL = "https://geocoding.geo.census.gov/geocoder/locations/address";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration RETRY_DELAY = Duration.ofMillis(500);

    private static final Logger log = LoggerFactory.getLogger(CensusGeocoderClient.class);

    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public GeocodeResult geocode(String addressLine1, String city, String stateCode, String postalCode) {
        String url = BASE_URL + "?benchmark=Public_AR_Current&format=json"
                + "&street=" + URLEncoder.encode(addressLine1, StandardCharsets.UTF_8)
                + "&city=" + URLEncoder.encode(city, StandardCharsets.UTF_8)
                + "&state=" + URLEncoder.encode(stateCode, StandardCharsets.UTF_8)
                + "&zip=" + URLEncoder.encode(postalCode, StandardCharsets.UTF_8);
        HttpRequest request =
                HttpRequest.newBuilder(URI.create(url)).timeout(REQUEST_TIMEOUT).GET().build();

        String lastFailureReason = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return CensusGeocoderResponseParser.parse(response.body());
                }
                if (response.statusCode() >= 400 && response.statusCode() < 500) {
                    // A 4xx will not resolve itself on retry -- most likely a malformed address value.
                    return new GeocodeResult.Failed("Census Geocoder returned HTTP " + response.statusCode());
                }
                lastFailureReason = "Census Geocoder returned HTTP " + response.statusCode();
            } catch (IOException e) {
                lastFailureReason = "Failed to call Census Geocoder: " + e.getMessage();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new GeocodeResult.Failed("Interrupted while calling Census Geocoder");
            }
            if (attempt < MAX_ATTEMPTS) {
                log.warn("Census Geocoder request failed (attempt {}/{}), retrying: {}", attempt, MAX_ATTEMPTS, lastFailureReason);
                sleep(RETRY_DELAY);
            }
        }
        return new GeocodeResult.Failed(lastFailureReason);
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
