package com.docfitai.backend.reference;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "specialty_taxonomy_mapping")
public class SpecialtyTaxonomyMapping {

    @EmbeddedId
    private SpecialtyTaxonomyMappingId id;

    protected SpecialtyTaxonomyMapping() {
    }

    public SpecialtyTaxonomyMapping(SpecialtyTaxonomyMappingId id) {
        this.id = id;
    }

    public SpecialtyTaxonomyMappingId getId() {
        return id;
    }
}
