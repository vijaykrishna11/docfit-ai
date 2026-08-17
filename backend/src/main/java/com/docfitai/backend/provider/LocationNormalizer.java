package com.docfitai.backend.provider;

import java.util.Locale;

/**
 * Deterministic, minimal address normalization used purely to build a location dedup identity
 * (CLAUDE.md 5, 38) -- never for display. Intentionally not a USPS-grade standardization system:
 * case, whitespace, and common punctuation only. Must stay byte-for-byte consistent with the SQL
 * expression in {@code V8__create_provider_location.sql} that backfilled existing rows, or a
 * re-import of an already-backfilled provider's address would create a duplicate location instead
 * of updating the existing one.
 */
public final class LocationNormalizer {

    private LocationNormalizer() {
    }

    public static String normalizedKey(
            String addressLine1, String addressLine2, String city, String stateCode, String postalCode) {
        String postal5 = postalCode == null
                ? ""
                : (postalCode.length() > 5 ? postalCode.substring(0, 5) : postalCode);
        return String.join(
                "|",
                normalizeToken(addressLine1),
                normalizeToken(addressLine2),
                normalizeToken(city),
                normalizeToken(stateCode),
                postal5);
    }

    private static String normalizeToken(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toUpperCase(Locale.ROOT).replaceAll("[.,]", "").replaceAll("\\s+", " ");
    }
}
