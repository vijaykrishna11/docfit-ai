package com.docfitai.backend.provider.geocoding;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Pure JSON-response parsing (CLAUDE.md "Census Geocoder Research" -- "CI must use stored
 * fixtures/mocked HTTP, never live Census API calls"). Fixtures below are real response bodies
 * captured this phase via direct requests against the live public API (WebFetch could not render
 * the official docs pages, so behavior was confirmed empirically rather than assumed).
 */
class CensusGeocoderResponseParserTest {

    // Real response for "4600 Silver Hill Rd, Washington, DC 20233" (the Census Bureau's own
    // headquarters address) -- a genuine strong/exact street-level match.
    private static final String STRONG_MATCH_FIXTURE = """
            {"result":{"input":{"address":{"address":"4600 Silver Hill Rd, Washington, DC 20233"},"benchmark":{"isDefault":true,"benchmarkDescription":"Public Address Ranges - Current Benchmark","id":"4","benchmarkName":"Public_AR_Current"}},"addressMatches":[{"tigerLine":{"side":"L","tigerLineId":"657091557"},"coordinates":{"x":-76.928365658124,"y":38.845053106269},"addressComponents":{"zip":"20233","streetName":"SILVER HILL","preType":"","city":"WASHINGTON","preDirection":"","suffixDirection":"","fromAddress":"4600","state":"DC","suffixType":"RD","toAddress":"4700","suffixQualifier":"","preQualifier":""},"matchedAddress":"4600 SILVER HILL RD, WASHINGTON, DC, 20233"}]}}
            """;

    // Real response for a deliberately nonexistent address -- genuine no-match, HTTP 200, empty array.
    private static final String NO_MATCH_FIXTURE = """
            {"result":{"input":{"address":{"address":"99999 Nonexistent Fake St, Nowhere, ZZ 00000"},"benchmark":{"isDefault":true,"benchmarkDescription":"Public Address Ranges - Current Benchmark","id":"4","benchmarkName":"Public_AR_Current"}},"addressMatches":[]}}
            """;

    @Test
    void parsesAStrongMatchIntoLatitudeAndLongitude() {
        GeocodeResult result = CensusGeocoderResponseParser.parse(STRONG_MATCH_FIXTURE);

        assertThat(result).isInstanceOf(GeocodeResult.Matched.class);
        GeocodeResult.Matched matched = (GeocodeResult.Matched) result;
        // y is latitude, x is longitude -- verified empirically (DC is ~38.8N, ~76.9W).
        assertThat(matched.latitude()).isEqualByComparingTo(new BigDecimal("38.845053106269"));
        assertThat(matched.longitude()).isEqualByComparingTo(new BigDecimal("-76.928365658124"));
        assertThat(matched.matchedAddress()).contains("SILVER HILL");
    }

    @Test
    void parsesAGenuineNoMatchWithoutError() {
        GeocodeResult result = CensusGeocoderResponseParser.parse(NO_MATCH_FIXTURE);

        assertThat(result).isInstanceOf(GeocodeResult.NoMatch.class);
    }

    @Test
    void aMalformedResponseIsReportedAsFailedNotAnException() {
        GeocodeResult result = CensusGeocoderResponseParser.parse("{ this is not valid json");

        assertThat(result).isInstanceOf(GeocodeResult.Failed.class);
        assertThat(((GeocodeResult.Failed) result).reason()).isNotBlank();
    }

    @Test
    void anEmptyResponseBodyIsReportedAsFailedNotAnException() {
        GeocodeResult result = CensusGeocoderResponseParser.parse("");

        assertThat(result).isInstanceOf(GeocodeResult.Failed.class);
    }
}
