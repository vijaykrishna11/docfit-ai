package com.docfitai.backend.provider.geocoding;

import java.math.BigDecimal;

/**
 * A geocode attempt's outcome, deliberately distinguishing "the geocoder ran and found nothing"
 * from "the geocoder call itself failed" (CLAUDE.md "Geocoding Pipeline") -- callers must never
 * treat these the same way (a failure is retriable/transient in nature, a genuine no-match is not).
 */
public sealed interface GeocodeResult {

    /** A real address-level match -- safe to upgrade a location's precision to {@code ADDRESS_GEOCODE}. */
    record Matched(BigDecimal latitude, BigDecimal longitude, String matchedAddress) implements GeocodeResult {
    }

    /** The geocoder ran successfully but found no match for this address -- not an error. */
    record NoMatch() implements GeocodeResult {
    }

    /** The call itself failed (timeout, HTTP error, malformed response) -- distinct from a genuine no-match. */
    record Failed(String reason) implements GeocodeResult {
    }
}
