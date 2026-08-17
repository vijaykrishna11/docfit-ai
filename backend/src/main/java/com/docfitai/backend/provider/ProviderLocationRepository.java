package com.docfitai.backend.provider;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderLocationRepository extends JpaRepository<ProviderLocation, Long> {

    Optional<ProviderLocation> findByProviderIdAndNormalizedKey(Long providerId, String normalizedKey);

    List<ProviderLocation> findByProviderIdOrderByPrimaryDescId(Long providerId);

    boolean existsByIdAndProviderId(Long id, Long providerId);

    /** Bounded candidate selection for the geocoding pipeline (CLAUDE.md "Geocoding Pipeline") -- never the whole table. */
    List<ProviderLocation> findByCoordinatePrecisionOrderById(CoordinatePrecision coordinatePrecision, Pageable pageable);
}
