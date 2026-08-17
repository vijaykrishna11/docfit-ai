package com.docfitai.backend.provider.geocoding;

import java.math.BigDecimal;
import java.util.List;
import tools.jackson.databind.ObjectMapper;

/**
 * Parses one Census Geocoder JSON response into a {@link GeocodeResult}. Pure/side-effect free (no
 * I/O) so it can be unit tested directly against stored fixtures, matching {@code NppesProviderMapper}'s
 * convention -- CI never needs a live call to exercise this.
 */
public final class CensusGeocoderResponseParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private CensusGeocoderResponseParser() {
    }

    public static GeocodeResult parse(String body) {
        try {
            CensusGeocoderResponse response = OBJECT_MAPPER.readValue(body, CensusGeocoderResponse.class);
            List<CensusGeocoderMatch> matches =
                    response.result() == null || response.result().addressMatches() == null
                            ? List.of()
                            : response.result().addressMatches();
            if (matches.isEmpty()) {
                return new GeocodeResult.NoMatch();
            }
            // Only ever take the first match -- multiple matches means genuine ambiguity, and this
            // parser deliberately does not try to disambiguate (CLAUDE.md "never blindly mark
            // EXACT... only upgrade on a legitimate address-level match").
            CensusGeocoderMatch match = matches.get(0);
            return new GeocodeResult.Matched(
                    BigDecimal.valueOf(match.coordinates().y()), BigDecimal.valueOf(match.coordinates().x()), match.matchedAddress());
        } catch (Exception e) {
            return new GeocodeResult.Failed("Malformed Census Geocoder response: " + e.getMessage());
        }
    }
}
