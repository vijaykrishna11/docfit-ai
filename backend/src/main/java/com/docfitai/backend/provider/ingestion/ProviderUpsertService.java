package com.docfitai.backend.provider.ingestion;

import com.docfitai.backend.provider.LocationNormalizer;
import com.docfitai.backend.provider.Provider;
import com.docfitai.backend.provider.ProviderEntityType;
import com.docfitai.backend.provider.ProviderLocation;
import com.docfitai.backend.provider.ProviderLocationRepository;
import com.docfitai.backend.provider.ProviderRepository;
import com.docfitai.backend.provider.ProviderTaxonomy;
import com.docfitai.backend.provider.ProviderTaxonomyId;
import com.docfitai.backend.provider.ProviderTaxonomyRepository;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent upsert of one {@link ProviderImportRecord}, shared by every provider importer
 * (NPPES today, a bounded CSV importer -- CLAUDE.md 19, 28). Running the same source record
 * through this twice produces the same provider/location/taxonomy counts, never duplicates
 * (CLAUDE.md 20): providers are matched by NPI, locations by a normalized address identity
 * (CLAUDE.md 5), and taxonomy pairs by their natural (provider, code) key.
 *
 * <p>Also detects and records a bounded set of meaningful changes on an already-known provider
 * (CLAUDE.md "Change Detection") -- never for a brand-new provider's initial import, since an
 * initial state is not a "change." See {@link ChangeType} for exactly which changes this can
 * detect and why others are deliberately out of scope.
 */
@Service
public class ProviderUpsertService {

    private final ProviderRepository providerRepository;
    private final ProviderLocationRepository providerLocationRepository;
    private final ProviderTaxonomyRepository providerTaxonomyRepository;
    private final ProviderChangeEventRepository changeEventRepository;

    public ProviderUpsertService(
            ProviderRepository providerRepository,
            ProviderLocationRepository providerLocationRepository,
            ProviderTaxonomyRepository providerTaxonomyRepository,
            ProviderChangeEventRepository changeEventRepository) {
        this.providerRepository = providerRepository;
        this.providerLocationRepository = providerLocationRepository;
        this.providerTaxonomyRepository = providerTaxonomyRepository;
        this.changeEventRepository = changeEventRepository;
    }

    /**
     * {@code @Transactional} here too, not just on the 2-arg overload -- a self-invoked call to
     * {@code upsert(record, null)} from inside this same bean bypasses Spring's proxy (and its
     * transactional advice) entirely, since self-invocation never goes back through the proxy.
     * Without this, every entity mutation that isn't itself a repository {@code save()} call
     * (e.g. {@code ProviderLocation.updateFrom}, the identity-change setters) would silently be
     * lost on a detached entity instead of being flushed -- found by a real, previously-passing
     * test failing after this overload was introduced.
     */
    @Transactional
    public UpsertOutcome upsert(ProviderImportRecord record) {
        return upsert(record, null);
    }

    @Transactional
    public UpsertOutcome upsert(ProviderImportRecord record, Long sourceImportId) {
        Optional<Provider> existingProvider = providerRepository.findByNpiNumber(record.identity().npiNumber());
        Provider provider;
        boolean providerCreated;
        if (existingProvider.isPresent()) {
            provider = existingProvider.get();
            providerCreated = false;
            detectIdentityChanges(provider, record.identity(), sourceImportId);
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
                ProviderLocation location = existingLocation.get();
                if (!providerCreated && !Objects.equals(location.getPhone(), locationRecord.phone())) {
                    recordChange(
                            provider.getId(),
                            ChangeType.PHONE_CHANGED,
                            location.getId(),
                            location.getPhone(),
                            locationRecord.phone(),
                            sourceImportId);
                }
                location.updateFrom(
                        locationRecord.phone(),
                        locationRecord.fax(),
                        locationRecord.latitude(),
                        locationRecord.longitude(),
                        locationRecord.coordinatePrecision());
                locationsUpdated++;
            } else {
                boolean isPrimary = !providerHasPrimary;
                ProviderLocation saved = providerLocationRepository.save(new ProviderLocation(
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
                if (!providerCreated) {
                    recordChange(
                            provider.getId(),
                            ChangeType.LOCATION_ADDED,
                            saved.getId(),
                            null,
                            locationRecord.addressLine1() + ", " + locationRecord.city(),
                            sourceImportId);
                }
                locationsCreated++;
            }
        }

        Set<String> existingTaxonomyCodes = providerCreated
                ? Set.of()
                : providerTaxonomyRepository.findByIdProviderId(provider.getId()).stream()
                        .map(t -> t.getId().getTaxonomyCode())
                        .collect(java.util.stream.Collectors.toSet());

        // save() on an entity with a fully-populated @EmbeddedId merges (update-or-insert) rather
        // than blindly inserting, so re-processing an already-known (provider, taxonomy) pair is
        // naturally idempotent -- no separate existence check needed for the upsert itself.
        for (ProviderTaxonomyRecord taxonomyRecord : record.taxonomies()) {
            providerTaxonomyRepository.save(
                    new ProviderTaxonomy(new ProviderTaxonomyId(provider.getId(), taxonomyRecord.taxonomyCode()), taxonomyRecord.primary()));
            if (!providerCreated && !existingTaxonomyCodes.contains(taxonomyRecord.taxonomyCode())) {
                recordChange(
                        provider.getId(), ChangeType.TAXONOMY_ADDED, null, null, taxonomyRecord.taxonomyCode(), sourceImportId);
            }
        }

        return new UpsertOutcome(providerCreated, locationsCreated, locationsUpdated);
    }

    private void detectIdentityChanges(Provider provider, ProviderIdentityRecord identity, Long sourceImportId) {
        if (provider.getEntityType() == ProviderEntityType.INDIVIDUAL) {
            if (!Objects.equals(provider.getFirstName(), identity.firstName())
                    || !Objects.equals(provider.getLastName(), identity.lastName())) {
                String oldName = joinName(provider.getFirstName(), provider.getLastName());
                String newName = joinName(identity.firstName(), identity.lastName());
                recordChange(provider.getId(), ChangeType.PROVIDER_NAME_CHANGED, null, oldName, newName, sourceImportId);
                provider.setFirstName(identity.firstName());
                provider.setLastName(identity.lastName());
            }
        } else if (!Objects.equals(provider.getOrganizationName(), identity.organizationName())) {
            recordChange(
                    provider.getId(),
                    ChangeType.ORGANIZATION_NAME_CHANGED,
                    null,
                    provider.getOrganizationName(),
                    identity.organizationName(),
                    sourceImportId);
            provider.setOrganizationName(identity.organizationName());
        }
    }

    private void recordChange(
            Long providerId, ChangeType changeType, Long providerLocationId, String oldValue, String newValue, Long sourceImportId) {
        changeEventRepository.save(
                new ProviderChangeEvent(providerId, changeType, providerLocationId, oldValue, newValue, sourceImportId, Instant.now()));
    }

    private static String joinName(String firstName, String lastName) {
        return ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
    }

    public record UpsertOutcome(boolean providerCreated, int locationsCreated, int locationsUpdated) {
    }
}
