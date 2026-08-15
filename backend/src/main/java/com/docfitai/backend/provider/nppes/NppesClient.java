package com.docfitai.backend.provider.nppes;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Thin client for the public NPI Registry / NPPES search API (https://npiregistry.cms.hhs.gov/api/). */
@Component
public class NppesClient {

    private static final String BASE_URL = "https://npiregistry.cms.hhs.gov/api/";

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NppesResponse searchByPostalCode(String postalCode, int limit) {
        String url = BASE_URL + "?version=2.1"
                + "&postal_code=" + URLEncoder.encode(postalCode, StandardCharsets.UTF_8)
                + "&enumeration_type=NPI-1"
                + "&limit=" + limit;
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException(
                        "NPPES API returned HTTP " + response.statusCode() + " for postal code " + postalCode);
            }
            return objectMapper.readValue(response.body(), NppesResponse.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to call NPPES API for postal code " + postalCode, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling NPPES API for postal code " + postalCode, e);
        }
    }
}
