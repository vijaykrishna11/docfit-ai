package com.docfitai.backend.reference.geoimport;

import com.docfitai.backend.reference.ZipGeography;
import com.docfitai.backend.reference.ZipGeographyRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent upsert of one {@link GeographyRecord}, keyed by ZIP code (the existing primary key --
 * CLAUDE.md "Geography Unique Key"). Re-importing the same source data updates rows in place
 * rather than duplicating them.
 */
@Service
public class GeographyUpsertService {

    private final ZipGeographyRepository zipGeographyRepository;

    public GeographyUpsertService(ZipGeographyRepository zipGeographyRepository) {
        this.zipGeographyRepository = zipGeographyRepository;
    }

    @Transactional
    public boolean upsert(GeographyRecord record, String sourceName, String sourceVersion) {
        Optional<ZipGeography> existing = zipGeographyRepository.findById(record.zipCode());
        Instant now = Instant.now();
        if (existing.isPresent()) {
            ZipGeography zip = existing.get();
            zip.setCity(record.city());
            zip.setStateCode(record.stateCode());
            zip.setCounty(record.county());
            zip.setLatitude(record.latitude());
            zip.setLongitude(record.longitude());
            zip.setProvenance(sourceName, sourceVersion, now);
            return false;
        }
        ZipGeography created =
                new ZipGeography(record.zipCode(), record.city(), record.stateCode(), record.latitude(), record.longitude());
        created.setCounty(record.county());
        created.setProvenance(sourceName, sourceVersion, now);
        zipGeographyRepository.save(created);
        return true;
    }
}
