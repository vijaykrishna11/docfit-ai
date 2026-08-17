package com.docfitai.backend.provider.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.docfitai.backend.provider.CoordinatePrecision;
import com.docfitai.backend.provider.Provider;
import com.docfitai.backend.provider.ProviderEntityType;
import com.docfitai.backend.provider.ProviderLocation;
import com.docfitai.backend.provider.ProviderLocationRepository;
import com.docfitai.backend.provider.ProviderRepository;
import com.docfitai.backend.provider.ProviderTaxonomyRepository;
import com.docfitai.backend.testsupport.PostgresIntegrationSupport;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Import idempotency (CLAUDE.md 20, 53): running the same source record through the upsert
 * service twice must never duplicate a provider, location, or taxonomy pair. Covers existing
 * provider + new location, existing provider + changed phone at an existing location, existing
 * provider + already-known taxonomy, and organization (NPI-2) providers.
 */
class ProviderUpsertServiceTest extends PostgresIntegrationSupport {

    @Autowired
    private ProviderUpsertService upsertService;

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private ProviderLocationRepository providerLocationRepository;

    @Autowired
    private ProviderTaxonomyRepository providerTaxonomyRepository;

    @Autowired
    private ProviderChangeEventRepository changeEventRepository;

    @Autowired
    private DataImportRepository dataImportRepository;

    @Test
    void importingTheSameRecordTwiceCreatesNothingTheSecondTime() {
        ProviderImportRecord record = individualRecord("8000000001", "1 Test Ave", "Long Beach", "CA", "90802");

        ProviderUpsertService.UpsertOutcome first = upsertService.upsert(record);
        ProviderUpsertService.UpsertOutcome second = upsertService.upsert(record);

        assertThat(first.providerCreated()).isTrue();
        assertThat(first.locationsCreated()).isEqualTo(1);
        assertThat(second.providerCreated()).isFalse();
        assertThat(second.locationsCreated()).isZero();
        assertThat(second.locationsUpdated()).isEqualTo(1);

        Provider provider = providerRepository.findByNpiNumber("8000000001").orElseThrow();
        assertThat(providerLocationRepository.findByProviderIdOrderByPrimaryDescId(provider.getId())).hasSize(1);
    }

    @Test
    void existingProviderGetsANewLocationWithoutDuplicatingTheFirst() {
        ProviderImportRecord initial = individualRecord("8000000002", "1 Office A", "Long Beach", "CA", "90802");
        upsertService.upsert(initial);

        ProviderImportRecord withSecondLocation = new ProviderImportRecord(
                initial.identity(),
                List.of(
                        initial.locations().get(0),
                        new ProviderLocationRecord(
                                "LOCATION", "2 Office B", null, "Long Beach", "CA", "90802", "562-555-0002", null,
                                new BigDecimal("33.770000"), new BigDecimal("-118.191000"), CoordinatePrecision.ZIP_CENTROID)),
                initial.taxonomies());

        ProviderUpsertService.UpsertOutcome outcome = upsertService.upsert(withSecondLocation);

        assertThat(outcome.providerCreated()).isFalse();
        assertThat(outcome.locationsCreated()).isEqualTo(1);
        assertThat(outcome.locationsUpdated()).isEqualTo(1);

        Provider provider = providerRepository.findByNpiNumber("8000000002").orElseThrow();
        List<ProviderLocation> locations = providerLocationRepository.findByProviderIdOrderByPrimaryDescId(provider.getId());
        assertThat(locations).hasSize(2);
        assertThat(locations).filteredOn(ProviderLocation::isPrimary).hasSize(1);
    }

    @Test
    void changedPhoneAtAnExistingLocationUpdatesRatherThanDuplicates() {
        ProviderImportRecord initial = individualRecord("8000000003", "1 Test Ave", "Long Beach", "CA", "90802");
        upsertService.upsert(initial);

        ProviderLocationRecord original = initial.locations().get(0);
        ProviderLocationRecord withNewPhone = new ProviderLocationRecord(
                original.addressPurpose(), original.addressLine1(), original.addressLine2(), original.city(), original.stateCode(),
                original.postalCode(), "562-555-9999", null, original.latitude(), original.longitude(), original.coordinatePrecision());
        upsertService.upsert(new ProviderImportRecord(initial.identity(), List.of(withNewPhone), initial.taxonomies()));

        Provider provider = providerRepository.findByNpiNumber("8000000003").orElseThrow();
        List<ProviderLocation> locations = providerLocationRepository.findByProviderIdOrderByPrimaryDescId(provider.getId());
        assertThat(locations).hasSize(1);
        assertThat(locations.get(0).getPhone()).isEqualTo("562-555-9999");

        List<ProviderChangeEvent> events = changeEventRepository.findByProviderIdOrderByCreatedAtDesc(provider.getId());
        assertThat(events).extracting(ProviderChangeEvent::getChangeType, ProviderChangeEvent::getNewValue)
                .containsExactly(tuple(ChangeType.PHONE_CHANGED, "562-555-9999"));
    }

    @Test
    void aBrandNewProvidersInitialImportRecordsNoChangeEvents() {
        ProviderImportRecord record = individualRecord("8000000006", "1 Test Ave", "Long Beach", "CA", "90802");
        upsertService.upsert(record);

        Provider provider = providerRepository.findByNpiNumber("8000000006").orElseThrow();
        assertThat(changeEventRepository.findByProviderIdOrderByCreatedAtDesc(provider.getId())).isEmpty();
    }

    @Test
    void aNameChangeOnAnExistingIndividualProviderIsDetectedAndApplied() {
        ProviderImportRecord initial = individualRecord("8000000007", "1 Test Ave", "Long Beach", "CA", "90802");
        upsertService.upsert(initial);

        DataImport dataImport = dataImportRepository.save(new DataImport("CSV", Instant.now()));

        ProviderIdentityRecord renamed =
                new ProviderIdentityRecord("8000000007", ProviderEntityType.INDIVIDUAL, "Testina", "Doctor", null);
        upsertService.upsert(new ProviderImportRecord(renamed, initial.locations(), initial.taxonomies()), dataImport.getId());

        Provider provider = providerRepository.findByNpiNumber("8000000007").orElseThrow();
        assertThat(provider.getFirstName()).isEqualTo("Testina");

        List<ProviderChangeEvent> events = changeEventRepository.findByProviderIdOrderByCreatedAtDesc(provider.getId());
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getChangeType()).isEqualTo(ChangeType.PROVIDER_NAME_CHANGED);
        assertThat(events.get(0).getOldValue()).isEqualTo("Test Doctor");
        assertThat(events.get(0).getNewValue()).isEqualTo("Testina Doctor");
        assertThat(events.get(0).getSourceImportId()).isEqualTo(dataImport.getId());
    }

    @Test
    void anOrganizationNameChangeIsDetectedAndApplied() {
        ProviderIdentityRecord identity =
                new ProviderIdentityRecord("8000000008", ProviderEntityType.ORGANIZATION, null, null, "Old Name Medical Group");
        ProviderImportRecord record = new ProviderImportRecord(
                identity,
                List.of(new ProviderLocationRecord(
                        "LOCATION", "1 Group Way", null, "Long Beach", "CA", "90802", null, null,
                        new BigDecimal("33.770000"), new BigDecimal("-118.191000"), CoordinatePrecision.ZIP_CENTROID)),
                List.of(new ProviderTaxonomyRecord("207RC0000X", true)));
        upsertService.upsert(record);

        ProviderIdentityRecord renamed =
                new ProviderIdentityRecord("8000000008", ProviderEntityType.ORGANIZATION, null, null, "New Name Medical Group");
        upsertService.upsert(new ProviderImportRecord(renamed, record.locations(), record.taxonomies()));

        Provider provider = providerRepository.findByNpiNumber("8000000008").orElseThrow();
        assertThat(provider.getOrganizationName()).isEqualTo("New Name Medical Group");
        assertThat(changeEventRepository.findByProviderIdOrderByCreatedAtDesc(provider.getId()))
                .extracting(ProviderChangeEvent::getChangeType)
                .containsExactly(ChangeType.ORGANIZATION_NAME_CHANGED);
    }

    @Test
    void aNewLocationOnAnExistingProviderRecordsALocationAddedEvent() {
        ProviderImportRecord initial = individualRecord("8000000009", "1 Office A", "Long Beach", "CA", "90802");
        upsertService.upsert(initial);

        ProviderImportRecord withSecondLocation = new ProviderImportRecord(
                initial.identity(),
                List.of(
                        initial.locations().get(0),
                        new ProviderLocationRecord(
                                "LOCATION", "2 Office B", null, "Long Beach", "CA", "90802", "562-555-0002", null,
                                new BigDecimal("33.770000"), new BigDecimal("-118.191000"), CoordinatePrecision.ZIP_CENTROID)),
                initial.taxonomies());
        upsertService.upsert(withSecondLocation);

        Provider provider = providerRepository.findByNpiNumber("8000000009").orElseThrow();
        assertThat(changeEventRepository.findByProviderIdOrderByCreatedAtDesc(provider.getId()))
                .extracting(ProviderChangeEvent::getChangeType)
                .containsExactly(ChangeType.LOCATION_ADDED);
    }

    @Test
    void aNewTaxonomyOnAnExistingProviderRecordsATaxonomyAddedEvent() {
        ProviderImportRecord initial = individualRecord("8000000010", "1 Test Ave", "Long Beach", "CA", "90802");
        upsertService.upsert(initial);

        ProviderImportRecord withNewTaxonomy = new ProviderImportRecord(
                initial.identity(),
                initial.locations(),
                List.of(new ProviderTaxonomyRecord("207RC0000X", true), new ProviderTaxonomyRecord("207RI0011X", false)));
        upsertService.upsert(withNewTaxonomy);

        Provider provider = providerRepository.findByNpiNumber("8000000010").orElseThrow();
        assertThat(changeEventRepository.findByProviderIdOrderByCreatedAtDesc(provider.getId()))
                .extracting(ProviderChangeEvent::getChangeType, ProviderChangeEvent::getNewValue)
                .containsExactly(tuple(ChangeType.TAXONOMY_ADDED, "207RI0011X"));
    }

    @Test
    void reimportingAnAlreadyKnownTaxonomyPairDoesNotDuplicateIt() {
        ProviderImportRecord record = individualRecord("8000000004", "1 Test Ave", "Long Beach", "CA", "90802");
        upsertService.upsert(record);
        upsertService.upsert(record);

        Provider provider = providerRepository.findByNpiNumber("8000000004").orElseThrow();
        assertThat(providerTaxonomyRepository.findAll().stream().filter(t -> t.getId().getProviderId().equals(provider.getId())))
                .hasSize(1);
    }

    @Test
    void organizationProviderIsPersistedWithOrganizationEntityType() {
        ProviderIdentityRecord identity =
                new ProviderIdentityRecord("8000000005", ProviderEntityType.ORGANIZATION, null, null, "Test Medical Group");
        ProviderImportRecord record = new ProviderImportRecord(
                identity,
                List.of(new ProviderLocationRecord(
                        "LOCATION", "1 Group Way", null, "Long Beach", "CA", "90802", null, null,
                        new BigDecimal("33.770000"), new BigDecimal("-118.191000"), CoordinatePrecision.ZIP_CENTROID)),
                List.of(new ProviderTaxonomyRecord("207RC0000X", true)));

        upsertService.upsert(record);

        Provider provider = providerRepository.findByNpiNumber("8000000005").orElseThrow();
        assertThat(provider.getEntityType()).isEqualTo(ProviderEntityType.ORGANIZATION);
        assertThat(provider.getOrganizationName()).isEqualTo("Test Medical Group");
    }

    private static ProviderImportRecord individualRecord(String npi, String line1, String city, String state, String postal) {
        ProviderIdentityRecord identity = new ProviderIdentityRecord(npi, ProviderEntityType.INDIVIDUAL, "Test", "Doctor", null);
        List<ProviderLocationRecord> locations = List.of(new ProviderLocationRecord(
                "LOCATION", line1, null, city, state, postal, "562-555-0001", null,
                new BigDecimal("33.770000"), new BigDecimal("-118.191000"), CoordinatePrecision.ZIP_CENTROID));
        List<ProviderTaxonomyRecord> taxonomies = List.of(new ProviderTaxonomyRecord("207RC0000X", true));
        return new ProviderImportRecord(identity, locations, taxonomies);
    }
}
