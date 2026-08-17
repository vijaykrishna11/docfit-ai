package com.docfitai.backend.account;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShortlistProviderRepository extends JpaRepository<ShortlistProvider, Long> {

    List<ShortlistProvider> findByShortlistId(Long shortlistId);

    long countByShortlistId(Long shortlistId);

    boolean existsByShortlistIdAndProviderId(Long shortlistId, Long providerId);

    void deleteByShortlistIdAndProviderId(Long shortlistId, Long providerId);
}
