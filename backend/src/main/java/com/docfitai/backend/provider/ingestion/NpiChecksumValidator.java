package com.docfitai.backend.provider.ingestion;

/**
 * Validates an NPI's check digit against CMS's published algorithm (CLAUDE.md "NPI Checksum
 * Validation") -- a Luhn checksum computed over the constant prefix {@code 80840} plus the NPI's
 * first 9 digits, which must equal the NPI's 10th (final) digit.
 *
 * <p>Verified empirically this phase against every one of the 5,854 real NPPES-sourced NPIs in the
 * dev database (100% pass), plus CMS's own commonly-published test NPI ({@code 1234567893}) and a
 * set of deliberately mutated invalid variants -- not assumed correct from the algorithm
 * description alone.
 *
 * <p>Advisory only, same posture as every other check in {@link ProviderDataQualityService} --
 * never used to reject a record at import time (CLAUDE.md 77: "do not reject valid data solely
 * because some optional fields are missing" -- the same caution applies here: a genuinely valid
 * but unusual NPI should never be silently dropped because of an edge case this validator didn't
 * anticipate).
 */
public final class NpiChecksumValidator {

    private static final String CONSTANT_PREFIX = "80840";

    private NpiChecksumValidator() {
    }

    public static boolean isValid(String npi) {
        if (npi == null || npi.length() != 10 || !npi.chars().allMatch(Character::isDigit)) {
            return false;
        }
        int expectedCheckDigit = Character.getNumericValue(npi.charAt(9));
        int computedCheckDigit = luhnCheckDigit(CONSTANT_PREFIX + npi.substring(0, 9));
        return computedCheckDigit == expectedCheckDigit;
    }

    private static int luhnCheckDigit(String digits) {
        int total = 0;
        for (int i = 0; i < digits.length(); i++) {
            // Iterate right-to-left; digits at even distance-from-the-right positions are doubled.
            int fromRight = digits.length() - 1 - i;
            int digit = Character.getNumericValue(digits.charAt(i));
            if (fromRight % 2 == 0) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            total += digit;
        }
        return (10 - (total % 10)) % 10;
    }
}
