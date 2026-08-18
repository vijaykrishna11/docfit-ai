package com.docfitai.backend.auth;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Stateless JWT API security. CSRF protection is intentionally disabled: the only
 * cookie-authenticated endpoints are /api/auth/refresh and /api/auth/logout, whose cookie is
 * scoped to /api/auth with SameSite=Lax (browsers withhold Lax cookies on cross-site POSTs,
 * which is exactly the CSRF vector CSRF tokens exist to close). Every other authenticated
 * endpoint uses a bearer access token kept in JS memory only, which a cross-site page cannot
 * read or attach -- inherently CSRF-immune.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // Render same-origin deployment (CLAUDE.md): Spring Boot also serves the built React SPA
    // (index.html + hashed assets) from this same origin, so a plain GET for the app shell or a
    // client-side route (e.g. "/providers/123", loaded directly via browser refresh) must be
    // public -- the SPA itself is not sensitive, only the API data it calls is, and that's already
    // gated per-endpoint below. Deliberately a predicate rather than an enumerated path list: it
    // covers every current and future frontend route automatically without drifting out of sync
    // with React Router's own route list, while still never permitting anything under /api or
    // /actuator that isn't already explicitly listed above -- those always take priority since
    // Spring Security evaluates authorizeHttpRequests rules in order, first match wins, and this
    // rule is deliberately the last one before the anyRequest() fallback.
    private static final RequestMatcher SPA_SHELL_REQUEST_MATCHER = request -> {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.equals("/api") && !path.startsWith("/api/") && !path.equals("/actuator") && !path.startsWith("/actuator/");
    };

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter, CorsConfigurationSource corsConfigurationSource)
            throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**")
                        .permitAll()
                        // Spring Boot's default error handling forwards failed requests to /error
                        // internally (an unauthenticated, container-level forward). Without this,
                        // that forward itself gets rejected by the rule below and the
                        // authenticationEntryPoint below overwrites every error response --
                        // regardless of its real status (400/404/409/...) -- with a bare, bodyless
                        // 401. This must stay permitAll for any real error body to ever reach the
                        // client.
                        .requestMatchers("/error")
                        .permitAll()
                        .requestMatchers("/api/auth/**")
                        .permitAll()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/specialties",
                                "/api/insurance-carriers",
                                "/api/insurance/**",
                                "/api/providers/**",
                                "/api/locations/**",
                                "/api/discovery/**")
                        .permitAll()
                        // Directory-data correction reports (CLAUDE.md "Report Privacy"): anonymous
                        // submission is allowed by design, rate-limited by IP either way
                        // (ReportRateLimiter) -- never require an account for a quick correction.
                        // If the caller happens to be signed in, JwtAuthenticationFilter still runs
                        // and populates Authentication regardless of this permitAll.
                        .requestMatchers(HttpMethod.POST, "/api/providers/*/reports")
                        .permitAll()
                        // Narrow on purpose: only the health probe is ever public. If
                        // management.endpoints.web.exposure.include is ever widened (e.g. to
                        // include env/beans/configprops), this rule must NOT accidentally expose
                        // it -- see docs/threat-model.md ("Configuration leak").
                        .requestMatchers("/actuator/health", "/actuator/health/**")
                        .permitAll()
                        .requestMatchers(SPA_SHELL_REQUEST_MATCHER)
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        (request, response, authException) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
