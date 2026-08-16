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
                                "/api/locations/**")
                        .permitAll()
                        // Narrow on purpose: only the health probe is ever public. If
                        // management.endpoints.web.exposure.include is ever widened (e.g. to
                        // include env/beans/configprops), this rule must NOT accidentally expose
                        // it -- see docs/threat-model.md ("Configuration leak").
                        .requestMatchers("/actuator/health", "/actuator/health/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        (request, response, authException) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
