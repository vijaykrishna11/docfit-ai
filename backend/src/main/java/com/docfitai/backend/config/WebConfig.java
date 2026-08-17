package com.docfitai.backend.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Centralized CORS config so the local Vite dev server can call the API during development.
 * Exposed as a {@link CorsConfigurationSource} bean so Spring Security's filter chain (which
 * runs before MVC) applies the exact same policy -- no wildcard origins, and credentials
 * (the refresh-token cookie) are only allowed for the explicitly listed origins.
 */
@Configuration
public class WebConfig {

    private final List<String> allowedOrigins;

    public WebConfig(@Value("${docfitai.cors.allowed-origins}") String allowedOriginsProperty) {
        this.allowedOrigins = List.of(allowedOriginsProperty.split(","));
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        // PATCH is required for display-name updates, shortlist/saved-search renames, and
        // reminder completion toggles -- omitting it silently 403s the browser's CORS preflight
        // for every PATCH endpoint (found via Playwright: reminder "mark as done" failed only in
        // a real browser, never in a same-origin/unit-test context that skips CORS entirely).
        configuration.setAllowedMethods(List.of("GET", "POST", "DELETE", "PUT", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
