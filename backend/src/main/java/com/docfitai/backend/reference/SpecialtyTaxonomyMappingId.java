package com.docfitai.backend.reference;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

/** Composite primary key for {@link SpecialtyTaxonomyMapping}, matching the (specialty_id, taxonomy_code) PK. */
@Embeddable
public class SpecialtyTaxonomyMappingId implements Serializable {

    @Column(name = "specialty_id")
    private Long specialtyId;

    @Column(name = "taxonomy_code", length = 10)
    private String taxonomyCode;

    protected SpecialtyTaxonomyMappingId() {
    }

    public SpecialtyTaxonomyMappingId(Long specialtyId, String taxonomyCode) {
        this.specialtyId = specialtyId;
        this.taxonomyCode = taxonomyCode;
    }

    public Long getSpecialtyId() {
        return specialtyId;
    }

    public String getTaxonomyCode() {
        return taxonomyCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SpecialtyTaxonomyMappingId that)) {
            return false;
        }
        return Objects.equals(specialtyId, that.specialtyId) && Objects.equals(taxonomyCode, that.taxonomyCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(specialtyId, taxonomyCode);
    }
}
