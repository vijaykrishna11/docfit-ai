package com.docfitai.backend.provider;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderRepository extends JpaRepository<Provider, Long> {

    boolean existsByNpiNumber(String npiNumber);

    Optional<Provider> findByNpiNumber(String npiNumber);
}
