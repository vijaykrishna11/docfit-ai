package com.docfitai.backend.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Fail-fast production configuration guard (CLAUDE.md 8, 56-57). Validation logic only -- pure
 * constructor check, no Spring annotations.
 *
 * <p>This is deliberately NOT a component-scanned {@code @Bean}: an earlier version was, but
 * that meant its check ran wherever the bean happened to fall in the dependency graph, which in
 * practice was AFTER {@code DataSource}/Flyway had already attempted a real database connection
 * (observed directly: booting with {@code spring.profiles.active=prod} and no {@code JWT_SECRET}
 * set still tried to authenticate to Postgres first). A bad production config should never get
 * far enough to touch the database at all. See {@link ProductionSafetyEnvironmentListener}, which
 * runs this check from {@code ApplicationEnvironmentPreparedEvent} -- before the
 * {@code ApplicationContext} is even created, so no bean can possibly run first.
 *
 * <p>Deliberately does NOT catch every possible misconfiguration; it catches the specific
 * "silently insecure by default" failure modes this codebase's own defaults could otherwise fall
 * into (a known placeholder JWT secret, a non-Secure refresh cookie, CORS still pointed at
 * localhost, synthetic insurance evidence left on). Local dev and the test profile are
 * unaffected -- this class is only ever invoked when "prod" is an active profile.
 */
public class ProductionSafetyValidator {

    // The exact insecure placeholder from application.properties's local-dev default, plus any
    // other obviously-a-placeholder value someone might paste in by mistake.
    private static final Set<String> KNOWN_INSECURE_JWT_SECRETS =
            Set.of("dev-only-insecure-secret-change-in-production-please-0123456789", "changeme", "secret", "");
    private static final int MIN_JWT_SECRET_LENGTH = 32;

    public ProductionSafetyValidator(
            String jwtSecret,
            boolean cookieSecure,
            String corsAllowedOrigins,
            boolean syntheticInsuranceEnabled,
            boolean csvImportEnabled,
            boolean geographyImportEnabled,
            boolean geocodeEnabled) {
        List<String> problems = new ArrayList<>();

        if (jwtSecret == null) {
            jwtSecret = "";
        }
        if (corsAllowedOrigins == null) {
            corsAllowedOrigins = "";
        }
        if (KNOWN_INSECURE_JWT_SECRETS.contains(jwtSecret) || jwtSecret.length() < MIN_JWT_SECRET_LENGTH) {
            problems.add(
                    "docfitai.auth.jwt-secret (JWT_SECRET) is missing, a known placeholder, or too short. "
                            + "Set a real, random 256-bit+ secret.");
        }
        if (!cookieSecure) {
            problems.add(
                    "docfitai.auth.cookie-secure (AUTH_COOKIE_SECURE) must be true in production -- "
                            + "the refresh-token cookie must never be sent over plain HTTP.");
        }
        if (corsAllowedOrigins.toLowerCase(java.util.Locale.ROOT).contains("localhost")
                || corsAllowedOrigins.contains("127.0.0.1")) {
            problems.add(
                    "docfitai.cors.allowed-origins (CORS_ALLOWED_ORIGINS) still references localhost/127.0.0.1 -- "
                            + "set it to the real production frontend origin(s).");
        }
        if (syntheticInsuranceEnabled) {
            problems.add(
                    "docfitai.insurance.synthetic-demo.enabled (DOCFIT_SYNTHETIC_INSURANCE_ENABLED) is true -- "
                            + "synthetic network evidence must never be seeded in production.");
        }
        if (csvImportEnabled) {
            problems.add(
                    "docfitai.import.csv.enabled (DOCFIT_PROVIDER_CSV_IMPORT_ENABLED) is true -- bulk provider "
                            + "import must be operator-triggered on demand, not left enabled on every startup.");
        }
        if (geographyImportEnabled) {
            problems.add(
                    "docfitai.import.geography.enabled (DOCFIT_GEOGRAPHY_IMPORT_ENABLED) is true -- geography "
                            + "reference import must be operator-triggered on demand, not left enabled on every startup.");
        }
        if (geocodeEnabled) {
            problems.add(
                    "docfitai.geocode.enabled (DOCFIT_GEOCODE_ENABLED) is true -- the geocoding batch must be "
                            + "operator-triggered on demand, not left enabled on every startup.");
        }

        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                    "Refusing to start under the 'prod' profile with unsafe configuration:\n - " + String.join("\n - ", problems));
        }
    }
}
