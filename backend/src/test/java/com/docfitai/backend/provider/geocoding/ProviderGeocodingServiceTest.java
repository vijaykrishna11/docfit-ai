package com.docfitai.backend.provider.geocoding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.docfitai.backend.provider.CoordinatePrecision;
import com.docfitai.backend.provider.ProviderLocation;
import com.docfitai.backend.provider.ProviderLocationRepository;
import com.docfitai.backend.testsupport.PostgresIntegrationSupport;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * CLAUDE.md "Geocoding Pipeline": exercises the pipeline against a mocked {@link
 * CensusGeocoderClient} -- CI never calls the live Census Geocoder API for this.
 */
@ExtendWith(MockitoExtension.class)
class ProviderGeocodingServiceTest extends PostgresIntegrationSupport {

    @Mock
    private CensusGeocoderClient client;

    @Autowired
    private ProviderLocationRepository providerLocationRepository;

    @Autowired
    private AddressGeocodeCacheRepository cacheRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ProviderGeocodingService newService() {
        return new ProviderGeocodingService(providerLocationRepository, cacheRepository, client);
    }

    @Test
    void aRealMatchUpgradesPrecisionAndCoordinatesToAddressGeocode() {
        Long providerId = insertProviderWithLocation(
                jdbcTemplate, "9999000001", "Geo", "One", "1 Match St", "Long Beach", "CA", "90802", null, 33.77, -118.19);
        when(client.geocode(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new GeocodeResult.Matched(new BigDecimal("33.771111"), new BigDecimal("-118.191111"), "1 MATCH ST, LONG BEACH, CA, 90802"));

        newService().geocodeBoundedBatch(500);

        ProviderLocation location = providerLocationRepository.findByProviderIdOrderByPrimaryDescId(providerId).get(0);
        assertThat(location.getCoordinatePrecision()).isEqualTo(CoordinatePrecision.ADDRESS_GEOCODE);
        assertThat(location.getLatitude()).isEqualByComparingTo(new BigDecimal("33.771111"));
        assertThat(location.getLongitude()).isEqualByComparingTo(new BigDecimal("-118.191111"));
    }

    @Test
    void aNoMatchRetainsTheExistingZipCentroidCoordinates() {
        Long providerId = insertProviderWithLocation(
                jdbcTemplate, "9999000002", "Geo", "Two", "2 NoMatch St", "Long Beach", "CA", "90802", null, 33.77, -118.19);
        when(client.geocode(anyString(), anyString(), anyString(), anyString())).thenReturn(new GeocodeResult.NoMatch());

        newService().geocodeBoundedBatch(500);

        ProviderLocation location = providerLocationRepository.findByProviderIdOrderByPrimaryDescId(providerId).get(0);
        assertThat(location.getCoordinatePrecision()).isEqualTo(CoordinatePrecision.ZIP_CENTROID);
        assertThat(location.getLatitude()).isEqualByComparingTo(new BigDecimal("33.770000"));
    }

    @Test
    void aFailedGeocodeRetainsCoordinatesAndIsNotCachedSoItsRetriedNextRun() {
        insertProviderWithLocation(
                jdbcTemplate, "9999000003", "Geo", "Three", "3 Fail St", "Long Beach", "CA", "90802", null, 33.77, -118.19);
        when(client.geocode(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new GeocodeResult.Failed("simulated timeout"));

        ProviderGeocodingService.GeocodingSummary summary = newService().geocodeBoundedBatch(500);

        assertThat(summary.failed()).isGreaterThanOrEqualTo(1);
        String normalizedKey = com.docfitai.backend.provider.LocationNormalizer.normalizedKey(
                "3 Fail St", null, "Long Beach", "CA", "90802");
        assertThat(cacheRepository.findById(normalizedKey)).isEmpty();
    }

    @Test
    void aCachedOutcomeIsReusedWithoutCallingTheClientAgain() {
        insertProviderWithLocation(
                jdbcTemplate, "9999000004", "Geo", "Four", "4 Cached St", "Long Beach", "CA", "90802", null, 33.77, -118.19);
        when(client.geocode(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new GeocodeResult.NoMatch());

        ProviderGeocodingService service = newService();
        service.geocodeBoundedBatch(500);
        service.geocodeBoundedBatch(500);

        // Other rows may exist in the shared test DB from other test classes' fixtures -- verify
        // with this test's exact address rather than anyString() so the assertion is specific to
        // what this test actually cares about: this one address was only geocoded once across
        // both runs (the second run must be a pure cache hit).
        verify(client, times(1)).geocode("4 Cached St", "Long Beach", "CA", "90802");
    }
}
