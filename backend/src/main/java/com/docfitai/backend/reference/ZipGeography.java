package com.docfitai.backend.reference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "zip_geography")
public class ZipGeography {

    @Id
    @Column(name = "zip_code", length = 5)
    private String zipCode;

    // Nullable: a real, legitimate LA County ZCTA can have no resolvable primary city (CLAUDE.md
    // "City Representation Limitations" -- see docs/la-county-geography-sources.md) when the
    // majority of its land area isn't inside any incorporated place or Census Designated Place.
    // Never fabricated -- left genuinely empty rather than guessed (V18 migration).
    @Column
    private String city;

    @Column(name = "state_code", nullable = false, length = 2)
    private String stateCode;

    // Nullable: only populated where a reliable source has actually confirmed it (CLAUDE.md
    // "County": "Do not infer county from city name"). Every row currently in this table does
    // have a verified county (see V16 migration), but a future geography import is not required
    // to supply one.
    @Column(name = "county")
    private String county;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal longitude;

    // Provenance (CLAUDE.md "Geography Provenance"): where these coordinates came from, which
    // source/version, when imported. Nullable -- not every row is required to carry it.
    @Column(name = "source_name")
    private String sourceName;

    @Column(name = "source_version")
    private String sourceVersion;

    @Column(name = "source_imported_at")
    private Instant sourceImportedAt;

    protected ZipGeography() {
    }

    public ZipGeography(String zipCode, String city, String stateCode, BigDecimal latitude, BigDecimal longitude) {
        this.zipCode = zipCode;
        this.city = city;
        this.stateCode = stateCode;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getZipCode() {
        return zipCode;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getStateCode() {
        return stateCode;
    }

    public void setStateCode(String stateCode) {
        this.stateCode = stateCode;
    }

    public String getCounty() {
        return county;
    }

    public void setCounty(String county) {
        this.county = county;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }

    public String getSourceName() {
        return sourceName;
    }

    public String getSourceVersion() {
        return sourceVersion;
    }

    public Instant getSourceImportedAt() {
        return sourceImportedAt;
    }

    public void setProvenance(String sourceName, String sourceVersion, Instant sourceImportedAt) {
        this.sourceName = sourceName;
        this.sourceVersion = sourceVersion;
        this.sourceImportedAt = sourceImportedAt;
    }
}
