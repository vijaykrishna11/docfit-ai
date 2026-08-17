package com.docfitai.backend.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Fail-fast production configuration guard (CLAUDE.md 8, 56-57, 93). These are plain constructor
 * calls, not a Spring context test -- the whole point is that this must fail before any bean
 * wiring or web server startup could happen.
 */
class ProductionSafetyValidatorTest {

    private static final String GOOD_SECRET = "a-genuinely-random-production-secret-value-1234567890";

    @Test
    void refusesTheKnownDevPlaceholderSecret() {
        assertThatThrownBy(() -> new ProductionSafetyValidator(
                        "dev-only-insecure-secret-change-in-production-please-0123456789",
                        true,
                        "https://app.docfit.example",
                        false,
                        false,
                        false,
                        false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jwt-secret");
    }

    @Test
    void refusesAShortSecret() {
        assertThatThrownBy(() -> new ProductionSafetyValidator("too-short", true, "https://app.docfit.example", false, false, false, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jwt-secret");
    }

    @Test
    void refusesAnInsecureCookie() {
        assertThatThrownBy(() -> new ProductionSafetyValidator(GOOD_SECRET, false, "https://app.docfit.example", false, false, false, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cookie-secure");
    }

    @Test
    void refusesLocalhostCorsOrigin() {
        assertThatThrownBy(() -> new ProductionSafetyValidator(GOOD_SECRET, true, "http://localhost:5173", false, false, false, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cors");
    }

    @Test
    void refusesSyntheticInsuranceEnabled() {
        assertThatThrownBy(() -> new ProductionSafetyValidator(GOOD_SECRET, true, "https://app.docfit.example", true, false, false, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("synthetic-demo");
    }

    @Test
    void refusesCsvImportEnabled() {
        assertThatThrownBy(() -> new ProductionSafetyValidator(GOOD_SECRET, true, "https://app.docfit.example", false, true, false, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("csv.enabled");
    }

    @Test
    void refusesGeographyImportEnabled() {
        assertThatThrownBy(() -> new ProductionSafetyValidator(GOOD_SECRET, true, "https://app.docfit.example", false, false, true, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("geography.enabled");
    }

    @Test
    void refusesGeocodeEnabled() {
        assertThatThrownBy(() -> new ProductionSafetyValidator(GOOD_SECRET, true, "https://app.docfit.example", false, false, false, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("geocode.enabled");
    }

    @Test
    void acceptsAFullySafeProductionConfiguration() {
        assertThatCode(() -> new ProductionSafetyValidator(GOOD_SECRET, true, "https://app.docfit.example", false, false, false, false))
                .doesNotThrowAnyException();
    }
}
