package com.docfitai.backend.reference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "npi_taxonomy")
public class NpiTaxonomy {

    @Id
    @Column(name = "taxonomy_code", length = 10)
    private String taxonomyCode;

    @Column(nullable = false)
    private String classification;

    private String specialization;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    protected NpiTaxonomy() {
    }

    public NpiTaxonomy(String taxonomyCode, String classification, String specialization, String displayName) {
        this.taxonomyCode = taxonomyCode;
        this.classification = classification;
        this.specialization = specialization;
        this.displayName = displayName;
    }

    public String getTaxonomyCode() {
        return taxonomyCode;
    }

    public String getClassification() {
        return classification;
    }

    public String getSpecialization() {
        return specialization;
    }

    public String getDisplayName() {
        return displayName;
    }
}
