package com.docfitai.backend.reference;

import static org.assertj.core.api.Assertions.assertThat;

import com.docfitai.backend.testsupport.PostgresIntegrationSupport;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ZipLookupTest extends PostgresIntegrationSupport {

    @Autowired
    private ZipGeographyRepository zipGeographyRepository;

    @Test
    void resolvesKnownLongBeachZip() {
        Optional<ZipGeography> result = zipGeographyRepository.findById("90802");

        assertThat(result).isPresent();
        assertThat(result.get().getCity()).isEqualTo("Long Beach");
        assertThat(result.get().getStateCode()).isEqualTo("CA");
    }

    @Test
    void unknownZipIsAbsent() {
        assertThat(zipGeographyRepository.findById("99999")).isEmpty();
    }
}
