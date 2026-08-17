package com.docfitai.backend.navigator;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderVerificationItemRepository extends JpaRepository<ProviderVerificationItem, Long> {

    Optional<ProviderVerificationItem> findByUserIdAndProviderIdAndVerificationType(
            Long userId, Long providerId, VerificationType verificationType);

    List<ProviderVerificationItem> findByUserIdAndProviderId(Long userId, Long providerId);

    List<ProviderVerificationItem> findByUserIdAndProviderIdIn(Long userId, List<Long> providerIds);

    List<ProviderVerificationItem> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}
