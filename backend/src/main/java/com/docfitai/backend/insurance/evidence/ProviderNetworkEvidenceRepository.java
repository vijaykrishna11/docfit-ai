package com.docfitai.backend.insurance.evidence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProviderNetworkEvidenceRepository extends JpaRepository<ProviderNetworkEvidence, Long> {

    @Query("SELECT e FROM ProviderNetworkEvidence e WHERE e.provider.id IN :providerIds AND e.plan.id = :planId")
    List<ProviderNetworkEvidence> findByProviderIdsAndPlanId(
            @Param("providerIds") List<Long> providerIds, @Param("planId") Long planId);

    @Query(
            "SELECT e FROM ProviderNetworkEvidence e WHERE e.provider.id IN :providerIds AND e.network.id IN :networkIds"
                    + " AND e.plan IS NULL")
    List<ProviderNetworkEvidence> findByProviderIdsAndNetworkIdsNoPlan(
            @Param("providerIds") List<Long> providerIds, @Param("networkIds") List<Long> networkIds);

    // Spring Data JPA converts an equality comparison against a null bound parameter into
    // "IS NULL" automatically, so passing planId=null/locationId=null here correctly matches
    // network-only / provider-wide (no specific plan/location) evidence rows.
    Optional<ProviderNetworkEvidence> findByProviderIdAndNetworkIdAndPlanIdAndSourceIdAndProviderLocationId(
            Long providerId, Long networkId, Long planId, Long sourceId, Long providerLocationId);

    boolean existsByProviderIdAndNetworkIdAndPlanIdAndSourceId(Long providerId, Long networkId, Long planId, Long sourceId);
}
