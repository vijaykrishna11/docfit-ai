package com.docfitai.backend.provider.ingestion;

import com.docfitai.backend.provider.LocationNormalizer;
import com.docfitai.backend.provider.Provider;
import com.docfitai.backend.provider.ProviderLocation;
import com.docfitai.backend.provider.ProviderLocationRepository;
import com.docfitai.backend.provider.ProviderRepository;
import com.docfitai.backend.provider.ProviderTaxonomy;
import com.docfitai.backend.provider.ProviderTaxonomyId;
import com.docfitai.backend.provider.ProviderTaxonomyRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent upsert of one {@link ProviderImportRecord}, shared by every provider importer
 * (NPPES today, a bounded CSV importer -- CLAUDE.md 19, 28). Running the same source record
 * through this twice produces the same provider/location/taxonomy counts, never duplicates
 * (CLAUDE.md 20): providers are matched by NPI, locations by a normalized address identity
 * (CLAUDE.md 5), and taxonomy pairs by their natural (provider, code) key.
 */
@Service
public class ProviderUpsertService {

    private final ProviderRepository providerRepository;
    private final ProviderLocationRepository providerLocationRepository;
    private final ProviderTaxonomyRepository providerTaxonomyRepository;

    public ProviderUpsertService(
            ProviderRepository providerRepository,
            ProviderLocationRepository providerLocationRepository,
            ProviderTaxonomyRepository providerTaxonomyRepository) {
        this.providerRepository = providerRepository;
        this.providerLocationRepository = providerLocationRepository;
        this.providerTaxonomyRepository = providerTaxonomyRepository;
    }

    @Transactional
    public UpsertOutcome upsert(ProviderImportRecord record) {
        Optional<Provider> existingProvider = providerRepository.findByNpiNumber(record.identity().npiNumber());
        Provider provider;
        boolean providerCreated;
        if (existingProvider.isPresent()) {
            provider = existingProvider.get();
            providerCreated = false;
        } else {
            provider = providerRepository.save(new Provider(
                    record.identity().npiNumber(),
                    record.identity().entityType(),
                    record.identity().firstName(),
                    record.identity().lastName(),
                    record.identity().organizationName()));
            providerCreated = true;
        }

        List<ProviderLocation> existingLocations = providerLocationRepository.findByProviderIdOrderByPrimaryDescId(provider.getId());
        boolean providerHasPrimary = existingLocations.stream().anyMatch(ProviderLocation::isPrimary);

        int locationsCreated = 0;
        int locationsUpdated = 0;
        for (ProviderLocationRecord locationRecord : record.locations()) {
            String normalizedKey = LocationNormalizer.normalizedKey(
                    locationRecord.addressLine1(),
                    locationRecord.addressLine2(),
                    locationRecord.city(),
                    locationRecord.stateCode(),
                    locationRecord.postalCode());
            Optional<ProviderLocation> existingLocation =
                    providerLocationRepository.findByProviderIdAndNormalizedKey(provider.getId(), normalizedKey);
            if (existingLocation.isPresent()) {
                existingLocation
                        .get()
                        .updateFrom(
                                locationRecord.phone(),
                                locationRecord.fax(),
                                locationRecord.latitude(),
                                locationRecord.longitude(),
                                locationRecord.coordinatePrecision());
                locationsUpdated++;
            } else {
                boolean isPrimary = !providerHasPrimary;
                providerLocationRepository.save(new ProviderLocation(
                        provider,
                        locationRecord.addressPurpose(),
                        locationRecord.addressLine1(),
                        locationRecord.addressLine2(),
                        locationRecord.city(),
                        locationRecord.stateCode(),
                        locationRecord.postalCode(),
                        locationRecord.phone(),
                        locationRecord.fax(),
                        locationRecord.latitude(),
                        locationRecord.longitude(),
                        locationRecord.coordinatePrecision(),
                        isPrimary));
                if (isPrimary) {
                    providerHasPrimary = true;
                }
                locationsCreated++;
            }
        }

        // save() on an entity with a fully-populated @EmbeddedId merges (update-or-insert) rather
        // than blindly inserting, so re-processing an already-known (provider, taxonomy) pair is
        // naturally idempotent -- no separate existence check needed.
        for (ProviderTaxonomyRecord taxonomyRecord : record.taxonomies()) {
            providerTaxonomyRepository.save(
                    new ProviderTaxonomy(new ProviderTaxonomyId(provider.getId(), taxonomyRecord.taxonomyCode()), taxonomyRecord.primary()));
        }

        return new UpsertOutcome(providerCreated, locationsCreated, locationsUpdated);
    }

    public record UpsertOutcome(boolean providerCreated, int locationsCreated, int locationsUpdated) {
    }
}
