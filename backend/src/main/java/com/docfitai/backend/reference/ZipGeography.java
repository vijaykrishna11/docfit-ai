package com.docfitai.backend.reference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "zip_geography")
public class ZipGeography {

    @Id
    @Column(name = "zip_code", length = 5)
    private String zipCode;

    @Column(nullable = false)
    private String city;

    @Column(name = "state_code", nullable = false, length = 2)
    private String stateCode;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal longitude;

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

    public String getStateCode() {
        return stateCode;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }
}
