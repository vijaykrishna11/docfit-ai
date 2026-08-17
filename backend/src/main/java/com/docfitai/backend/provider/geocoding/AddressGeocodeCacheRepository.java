package com.docfitai.backend.provider.geocoding;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AddressGeocodeCacheRepository extends JpaRepository<AddressGeocodeCache, String> {
}
