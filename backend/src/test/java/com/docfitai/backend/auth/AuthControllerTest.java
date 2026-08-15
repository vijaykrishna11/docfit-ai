package com.docfitai.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.docfitai.backend.auth.dto.AuthResponseDto;
import com.docfitai.backend.testsupport.PostgresIntegrationSupport;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
class AuthControllerTest extends PostgresIntegrationSupport {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String json(Object value) {
        return objectMapper.writeValueAsString(value);
    }

    @Test
    void registerCreatesAccountAndReturnsAccessTokenAndRefreshCookie() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "auth-register@example.com", "password", "TestPassword123"))))
                .andExpect(status().isCreated())
                .andReturn();

        AuthResponseDto response = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponseDto.class);
        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.user().email()).isEqualTo("auth-register@example.com");

        Cookie refreshCookie = result.getResponse().getCookie("docfit_refresh");
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie.isHttpOnly()).isTrue();
        assertThat(refreshCookie.getValue()).isNotBlank();
    }

    @Test
    void duplicateRegistrationIsRejected() throws Exception {
        String email = "auth-dup@example.com";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", "TestPassword123"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", "AnotherPassword123"))))
                .andExpect(status().isConflict());
    }

    @Test
    void loginSucceedsWithCorrectPasswordAndFailsWithWrongPassword() throws Exception {
        String email = "auth-login@example.com";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", "CorrectPassword123"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", "CorrectPassword123"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", "WrongPassword999"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRotatesTokenAndOldRefreshTokenCanNoLongerBeUsed() throws Exception {
        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "auth-refresh@example.com", "password", "TestPassword123"))))
                .andExpect(status().isCreated())
                .andReturn();
        Cookie originalRefreshCookie = registerResult.getResponse().getCookie("docfit_refresh");

        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh").cookie(originalRefreshCookie))
                .andExpect(status().isOk())
                .andReturn();
        Cookie rotatedRefreshCookie = refreshResult.getResponse().getCookie("docfit_refresh");
        assertThat(rotatedRefreshCookie.getValue()).isNotEqualTo(originalRefreshCookie.getValue());

        // The original (now-rotated-away) refresh token must be rejected.
        mockMvc.perform(post("/api/auth/refresh").cookie(originalRefreshCookie)).andExpect(status().isUnauthorized());

        // But the new one still works.
        mockMvc.perform(post("/api/auth/refresh").cookie(rotatedRefreshCookie)).andExpect(status().isOk());
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "auth-logout@example.com", "password", "TestPassword123"))))
                .andExpect(status().isCreated())
                .andReturn();
        Cookie refreshCookie = registerResult.getResponse().getCookie("docfit_refresh");

        mockMvc.perform(post("/api/auth/logout").cookie(refreshCookie)).andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie)).andExpect(status().isUnauthorized());
    }

    @Test
    void meRequiresAuthenticationAndReturnsCurrentUserWhenAuthenticated() throws Exception {
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());

        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "auth-me@example.com", "password", "TestPassword123"))))
                .andExpect(status().isCreated())
                .andReturn();
        AuthResponseDto response =
                objectMapper.readValue(registerResult.getResponse().getContentAsString(), AuthResponseDto.class);

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + response.accessToken()))
                .andExpect(status().isOk());
    }
}
