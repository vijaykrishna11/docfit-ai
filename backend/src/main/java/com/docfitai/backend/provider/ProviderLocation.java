package com.docfitai.backend.provider;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "provider_location")
public class ProviderLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @Column(name = "address_purpose", nullable = false)
    private String addressPurpose;

    @Column(name = "address_line_1", nullable = false)
    private String addressLine1;

    @Column(name = "address_line_2")
    private String addressLine2;

    @Column(nullable = false)
    private String city;

    @Column(name = "state_code", nullable = false, length = 2)
    private String stateCode;

    @Column(name = "postal_code", nullable = false, length = 10)
    private String postalCode;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    private String phone;

    private String fax;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "coordinate_precision", nullable = false)
    private CoordinatePrecision coordinatePrecision;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "normalized_key", nullable = false)
    private String normalizedKey;

    @Column(name = "source_last_updated_at")
    private Instant sourceLastUpdatedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected ProviderLocation() {
    }

    public ProviderLocation(
            Provider provider,
            String addressPurpose,
            String addressLine1,
            String addressLine2,
            String city,
            String stateCode,
            String postalCode,
            String phone,
            String fax,
            BigDecimal latitude,
            BigDecimal longitude,
            CoordinatePrecision coordinatePrecision,
            boolean primary) {
        this.provider = provider;
        this.addressPurpose = addressPurpose;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.city = city;
        this.stateCode = stateCode;
        this.postalCode = postalCode;
        this.countryCode = "US";
        this.phone = phone;
        this.fax = fax;
        this.latitude = latitude;
        this.longitude = longitude;
        this.coordinatePrecision = coordinatePrecision;
        this.primary = primary;
        this.normalizedKey = LocationNormalizer.normalizedKey(addressLine1, addressLine2, city, stateCode, postalCode);
    }

    /** Applied on re-import when the same normalized location is seen again, in case phone/coordinates changed. */
    public void updateFrom(String phone, String fax, BigDecimal latitude, BigDecimal longitude, CoordinatePrecision coordinatePrecision) {
        this.phone = phone;
        this.fax = fax;
        this.latitude = latitude;
        this.longitude = longitude;
        this.coordinatePrecision = coordinatePrecision;
    }

    /**
     * Applied only by the operator-controlled geocoding pipeline ({@code ProviderGeocodingService})
     * on a legitimate address-level match -- deliberately touches only coordinates/precision, never
     * phone/fax (CLAUDE.md "Geocoding Pipeline": never blindly mark EXACT, only upgrade on a real
     * match; a failed geocode must retain the existing ZIP-centroid coordinates rather than null
     * them out, which is why this method is never called for a NoMatch/Failed outcome).
     */
    public void upgradeToAddressGeocode(BigDecimal latitude, BigDecimal longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.coordinatePrecision = CoordinatePrecision.ADDRESS_GEOCODE;
    }

    public Long getId() {
        return id;
    }

    public Provider getProvider() {
        return provider;
    }

    public String getAddressPurpose() {
        return addressPurpose;
    }

    public String getAddressLine1() {
        return addressLine1;
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public String getCity() {
        return city;
    }

    public String getStateCode() {
        return stateCode;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public String getPhone() {
        return phone;
    }

    public String getFax() {
        return fax;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public CoordinatePrecision getCoordinatePrecision() {
        return coordinatePrecision;
    }

    public boolean isPrimary() {
        return primary;
    }

    public String getNormalizedKey() {
        return normalizedKey;
    }

    public Instant getSourceLastUpdatedAt() {
        return sourceLastUpdatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
