package com.docfitai.backend.provider;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "provider_taxonomy")
public class ProviderTaxonomy {

    @EmbeddedId
    private ProviderTaxonomyId id;

    @Column(name = "primary_taxonomy", nullable = false)
    private boolean primaryTaxonomy;

    protected ProviderTaxonomy() {
    }

    public ProviderTaxonomy(ProviderTaxonomyId id, boolean primaryTaxonomy) {
        this.id = id;
        this.primaryTaxonomy = primaryTaxonomy;
    }

    public ProviderTaxonomyId getId() {
        return id;
    }

    public boolean isPrimaryTaxonomy() {
        return primaryTaxonomy;
    }
}
