package com.docfitai.backend.provider;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderLocationRepository extends JpaRepository<ProviderLocation, Long> {

    Optional<ProviderLocation> findByProviderIdAndNormalizedKey(Long providerId, String normalizedKey);

    List<ProviderLocation> findByProviderIdOrderByPrimaryDescId(Long providerId);

    boolean existsByIdAndProviderId(Long id, Long providerId);
}
