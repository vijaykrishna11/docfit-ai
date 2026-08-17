package com.docfitai.backend.provider.ingestion;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderChangeEventRepository extends JpaRepository<ProviderChangeEvent, Long> {

    List<ProviderChangeEvent> findByProviderIdOrderByCreatedAtDesc(Long providerId);
}
