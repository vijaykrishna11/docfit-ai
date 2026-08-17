package com.docfitai.backend.provider.geocoding;

import com.docfitai.backend.provider.CoordinatePrecision;
import com.docfitai.backend.provider.LocationNormalizer;
import com.docfitai.backend.provider.ProviderLocation;
import com.docfitai.backend.provider.ProviderLocationRepository;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Operator-controlled address geocoding pipeline (CLAUDE.md "Geocoding Pipeline"). Never called
 * from {@code GET /api/providers/search} or any other request path -- ingestion/maintenance only,
 * triggered via {@link GeocodingRunner}.
 *
 * <p>Only ever considers {@code ZIP_CENTROID} locations (never re-touches an already-{@code EXACT}
 * or already-{@code ADDRESS_GEOCODE} row). Caches every outcome (match, no-match, or failure) by
 * normalized address so an unchanged address is never re-geocoded on a subsequent run. A failed or
 * no-match result never modifies the location's existing coordinates -- the ZIP centroid is
 * retained rather than nulled out.
 */
@Service
public class ProviderGeocodingService {

    private static final Logger log = LoggerFactory.getLogger(ProviderGeocodingService.class);
    // A hard safety ceiling regardless of what an operator requests -- geocoding is meant to be a
    // deliberate, bounded batch, not an unbounded sweep of the whole table in one run.
    private static final int MAX_RECORDS_PER_RUN = 2000;

    private final ProviderLocationRepository providerLocationRepository;
    private final AddressGeocodeCacheRepository cacheRepository;
    private final CensusGeocoderClient client;

    public ProviderGeocodingService(
            ProviderLocationRepository providerLocationRepository,
            AddressGeocodeCacheRepository cacheRepository,
            CensusGeocoderClient client) {
        this.providerLocationRepository = providerLocationRepository;
        this.cacheRepository = cacheRepository;
        this.client = client;
    }

    public GeocodingSummary geocodeBoundedBatch(int requestedMaxRecords) {
        int bound = Math.min(Math.max(requestedMaxRecords, 0), MAX_RECORDS_PER_RUN);
        List<ProviderLocation> candidates = providerLocationRepository.findByCoordinatePrecisionOrderById(
                CoordinatePrecision.ZIP_CENTROID, PageRequest.of(0, bound));

        int upgraded = 0;
        int cacheHits = 0;
        int noMatch = 0;
        int failed = 0;
        for (ProviderLocation location : candidates) {
            String normalizedAddress = LocationNormalizer.normalizedKey(
                    location.getAddressLine1(), location.getAddressLine2(), location.getCity(), location.getStateCode(), location.getPostalCode());

            Optional<AddressGeocodeCache> cached = cacheRepository.findById(normalizedAddress);
            GeocodeResult result;
            if (cached.isPresent()) {
                result = fromCache(cached.get());
                cacheHits++;
            } else {
                result = client.geocode(location.getAddressLine1(), location.getCity(), location.getStateCode(), location.getPostalCode());
                // A FAILED outcome (timeout, transient HTTP error) is deliberately NOT cached --
                // it should be retried on a later run, unlike a MATCHED/NO_MATCH outcome, which is
                // a stable, deterministic fact about the address (CLAUDE.md "Geocoding Pipeline").
                if (!(result instanceof GeocodeResult.Failed)) {
                    cacheRepository.save(toCacheEntry(normalizedAddress, result));
                }
            }

            switch (result) {
                case GeocodeResult.Matched matched -> {
                    location.upgradeToAddressGeocode(matched.latitude(), matched.longitude());
                    providerLocationRepository.save(location);
                    upgraded++;
                }
                case GeocodeResult.NoMatch ignored -> noMatch++;
                case GeocodeResult.Failed f -> {
                    log.warn("Geocode failed for provider_location {}: {}", location.getId(), f.reason());
                    failed++;
                }
            }
        }

        GeocodingSummary summary = new GeocodingSummary(candidates.size(), upgraded, cacheHits, noMatch, failed);
        log.info(
                "Geocoding batch complete: candidates={} upgraded={} cacheHits={} noMatch={} failed={}",
                summary.candidatesConsidered(), summary.upgraded(), summary.cacheHits(), summary.noMatch(), summary.failed());
        return summary;
    }

    private static GeocodeResult fromCache(AddressGeocodeCache cache) {
        return switch (cache.getMatchStatus()) {
            case "MATCHED" -> new GeocodeResult.Matched(cache.getLatitude(), cache.getLongitude(), cache.getMatchedAddress());
            case "NO_MATCH" -> new GeocodeResult.NoMatch();
            default -> new GeocodeResult.Failed(cache.getFailureReason());
        };
    }

    private static AddressGeocodeCache toCacheEntry(String normalizedAddress, GeocodeResult result) {
        return switch (result) {
            case GeocodeResult.Matched matched ->
                    AddressGeocodeCache.matched(normalizedAddress, matched.latitude(), matched.longitude(), matched.matchedAddress());
            case GeocodeResult.NoMatch ignored -> AddressGeocodeCache.noMatch(normalizedAddress);
            case GeocodeResult.Failed f -> AddressGeocodeCache.failed(normalizedAddress, f.reason());
        };
    }

    public record GeocodingSummary(int candidatesConsidered, int upgraded, int cacheHits, int noMatch, int failed) {
    }
}
