package com.docfitai.backend.account;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavedProviderRepository extends JpaRepository<SavedProvider, Long> {

    List<SavedProvider> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<SavedProvider> findByUserIdAndProviderId(Long userId, Long providerId);

    boolean existsByUserIdAndProviderId(Long userId, Long providerId);

    void deleteByUserIdAndProviderId(Long userId, Long providerId);

    void deleteByUserId(Long userId);
}
