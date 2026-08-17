package com.docfitai.backend.insurance;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InsurancePlanRepository extends JpaRepository<InsurancePlan, Long> {

    List<InsurancePlan> findByPayerIdAndActiveTrue(Long payerId);

    Optional<InsurancePlan> findByPayerIdAndExternalPlanIdentifier(Long payerId, String externalPlanIdentifier);

    boolean existsByPayerId(Long payerId);
}
