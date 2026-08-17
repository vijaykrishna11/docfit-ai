package com.docfitai.backend.navigator;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderNavigationRepository extends JpaRepository<ProviderNavigation, Long> {

    Optional<ProviderNavigation> findByUserIdAndProviderId(Long userId, Long providerId);

    List<ProviderNavigation> findByUserIdAndProviderIdIn(Long userId, List<Long> providerIds);

    List<ProviderNavigation> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}
