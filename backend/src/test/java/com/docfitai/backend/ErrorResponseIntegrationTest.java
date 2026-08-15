package com.docfitai.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.docfitai.backend.testsupport.PostgresIntegrationSupport;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Runs against a real embedded servlet container (RANDOM_PORT) using a plain JDK HttpClient, not
 * MockMvc, because Spring Boot's default error handling forwards failed requests to /error
 * internally -- a real container-level dispatch that MockMvc does not faithfully reproduce. A
 * prior regression (the /error path wasn't permitted in SecurityConfig) caused every error
 * response, regardless of its real status, to come back as a bare 401 with no body; the existing
 * MockMvc-based tests never caught it because they don't exercise this dispatch. This class
 * exists to catch that class of bug if it recurs.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ErrorResponseIntegrationTest extends PostgresIntegrationSupport {

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void unknownProviderReturnsRealNotFoundBodyNotABare401() throws Exception {
        HttpResponse<String> response = get("/api/providers/999999999");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("\"message\"");
        assertThat(response.body()).doesNotContain("\"status\":401");
    }

    @Test
    void invalidSearchReturnsRealBadRequestBodyNotABare401() throws Exception {
        HttpResponse<String> response = get("/api/providers/search?specialty=CARDIOLOGY");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("\"message\"");
    }

    @Test
    void loginWithWrongPasswordReturnsRealUnauthorizedMessage() throws Exception {
        post(
                "/api/auth/register",
                "{\"email\":\"error-response-test@example.com\",\"password\":\"password123\"}");

        HttpResponse<String> response = post(
                "/api/auth/login",
                "{\"email\":\"error-response-test@example.com\",\"password\":\"wrong-password\"}");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("\"message\":\"Invalid email or password.\"");
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .GET()
                .build();
        return httpClient.send(request, BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String jsonBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(jsonBody))
                .build();
        return httpClient.send(request, BodyHandlers.ofString());
    }
}
