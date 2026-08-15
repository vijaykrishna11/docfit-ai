package com.docfitai.backend.provider;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

/** Composite primary key for {@link ProviderTaxonomy}, matching the (provider_id, taxonomy_code) PK. */
@Embeddable
public class ProviderTaxonomyId implements Serializable {

    @Column(name = "provider_id")
    private Long providerId;

    @Column(name = "taxonomy_code", length = 10)
    private String taxonomyCode;

    protected ProviderTaxonomyId() {
    }

    public ProviderTaxonomyId(Long providerId, String taxonomyCode) {
        this.providerId = providerId;
        this.taxonomyCode = taxonomyCode;
    }

    public Long getProviderId() {
        return providerId;
    }

    public String getTaxonomyCode() {
        return taxonomyCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProviderTaxonomyId that)) {
            return false;
        }
        return Objects.equals(providerId, that.providerId) && Objects.equals(taxonomyCode, that.taxonomyCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(providerId, taxonomyCode);
    }
}
