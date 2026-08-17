package com.docfitai.backend.insurance;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InsuranceNetworkRepository extends JpaRepository<InsuranceNetwork, Long> {

    List<InsuranceNetwork> findByPayerId(Long payerId);

    Optional<InsuranceNetwork> findByPayerIdAndExternalNetworkIdentifier(Long payerId, String externalNetworkIdentifier);
}
