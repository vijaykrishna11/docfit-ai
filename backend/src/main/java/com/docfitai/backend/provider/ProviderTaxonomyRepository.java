package com.docfitai.backend.provider;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderTaxonomyRepository extends JpaRepository<ProviderTaxonomy, ProviderTaxonomyId> {

    List<ProviderTaxonomy> findByIdProviderId(Long providerId);
}
