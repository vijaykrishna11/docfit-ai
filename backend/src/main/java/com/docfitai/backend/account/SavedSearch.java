package com.docfitai.backend.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * An explicitly user-saved search. Privacy-critical: a row here only ever exists because the
 * user clicked "Save this search" -- DocFit AI never automatically records search history.
 */
@Entity
@Table(name = "saved_search")
public class SavedSearch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    private String name;

    @Column(name = "specialty_code", nullable = false)
    private String specialtyCode;

    @Column(name = "location_text")
    private String locationText;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(nullable = false)
    private int radius;

    @Column(nullable = false)
    private String sort;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SavedSearch() {
    }

    public SavedSearch(
            Long userId,
            String name,
            String specialtyCode,
            String locationText,
            BigDecimal latitude,
            BigDecimal longitude,
            int radius,
            String sort,
            Instant createdAt) {
        this.userId = userId;
        this.name = name;
        this.specialtyCode = specialtyCode;
        this.locationText = locationText;
        this.latitude = latitude;
        this.longitude = longitude;
        this.radius = radius;
        this.sort = sort;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSpecialtyCode() {
        return specialtyCode;
    }

    public String getLocationText() {
        return locationText;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public int getRadius() {
        return radius;
    }

    public String getSort() {
        return sort;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
