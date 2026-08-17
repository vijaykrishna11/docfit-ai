package com.docfitai.backend.provider.geocoding;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * One cached geocode outcome, keyed by {@code normalized_address} (same normalization
 * {@code LocationNormalizer} uses for provider_location dedup) so an unchanged address is never
 * re-geocoded (CLAUDE.md "Geocoding Pipeline").
 */
@Entity
@Table(name = "address_geocode_cache")
public class AddressGeocodeCache {

    @Id
    @Column(name = "normalized_address")
    private String normalizedAddress;

    @Column(name = "match_status", nullable = false)
    private String matchStatus;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "matched_address")
    private String matchedAddress;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "geocoded_at", insertable = false, updatable = false)
    private Instant geocodedAt;

    protected AddressGeocodeCache() {
    }

    public static AddressGeocodeCache matched(String normalizedAddress, BigDecimal latitude, BigDecimal longitude, String matchedAddress) {
        AddressGeocodeCache cache = new AddressGeocodeCache();
        cache.normalizedAddress = normalizedAddress;
        cache.matchStatus = "MATCHED";
        cache.latitude = latitude;
        cache.longitude = longitude;
        cache.matchedAddress = truncate(matchedAddress, 300);
        return cache;
    }

    public static AddressGeocodeCache noMatch(String normalizedAddress) {
        AddressGeocodeCache cache = new AddressGeocodeCache();
        cache.normalizedAddress = normalizedAddress;
        cache.matchStatus = "NO_MATCH";
        return cache;
    }

    public static AddressGeocodeCache failed(String normalizedAddress, String failureReason) {
        AddressGeocodeCache cache = new AddressGeocodeCache();
        cache.normalizedAddress = normalizedAddress;
        cache.matchStatus = "FAILED";
        cache.failureReason = truncate(failureReason, 300);
        return cache;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    public String getNormalizedAddress() {
        return normalizedAddress;
    }

    public String getMatchStatus() {
        return matchStatus;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public String getMatchedAddress() {
        return matchedAddress;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getGeocodedAt() {
        return geocodedAt;
    }
}
