package com.docfitai.backend.provider.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * CLAUDE.md "NPI Checksum Validation" -- verified empirically this phase against every one of the
 * 5,854 real NPPES-sourced NPIs in the dev database (100% pass, not just these samples) before
 * being wired into the app at all.
 */
class NpiChecksumValidatorTest {

    @Test
    void acceptsCmsPublishedTestNpi() {
        // The commonly-published example NPI from CMS's own implementation documentation.
        assertThat(NpiChecksumValidator.isValid("1234567893")).isTrue();
    }

    @Test
    void acceptsRealNpisSampledFromLiveNppesData() {
        // Sampled from the real LA County provider import this phase -- genuine live NPPES data.
        assertThat(NpiChecksumValidator.isValid("1851015176")).isTrue();
        assertThat(NpiChecksumValidator.isValid("1275765612")).isTrue();
        assertThat(NpiChecksumValidator.isValid("1285625178")).isTrue();
        assertThat(NpiChecksumValidator.isValid("1790027886")).isTrue();
        assertThat(NpiChecksumValidator.isValid("1295418887")).isTrue();
    }

    @Test
    void rejectsEveryWrongCheckDigitForAnOtherwiseValidNpi() {
        String first9 = "185101517";
        for (int wrongDigit = 0; wrongDigit <= 9; wrongDigit++) {
            if (wrongDigit == 6) {
                continue; // the real, correct check digit for this NPI
            }
            assertThat(NpiChecksumValidator.isValid(first9 + wrongDigit))
                    .as("check digit %d should be rejected", wrongDigit)
                    .isFalse();
        }
    }

    @Test
    void rejectsWrongLength() {
        assertThat(NpiChecksumValidator.isValid("123456789")).isFalse();
        assertThat(NpiChecksumValidator.isValid("12345678901")).isFalse();
    }

    @Test
    void rejectsNonDigitCharacters() {
        assertThat(NpiChecksumValidator.isValid("12345ABCDE")).isFalse();
    }

    @Test
    void rejectsNull() {
        assertThat(NpiChecksumValidator.isValid(null)).isFalse();
    }
}
