package com.docfitai.backend.provider.nppes;

import com.docfitai.backend.provider.ProviderEntityType;
import com.docfitai.backend.provider.ingestion.ProviderIdentityRecord;
import com.docfitai.backend.provider.ingestion.ProviderTaxonomyRecord;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pure mapping/filtering logic from a raw NPPES search result to the fields DocFit AI persists.
 * Kept side-effect free (no network, no Spring context, no database) so it can be unit tested
 * directly. Geocoding (turning a postal code into coordinates) is deliberately NOT done here --
 * that needs {@code zip_geography} lookups, which belong to the caller (NppesImportRunner).
 *
 * <p>Supports both individual (NPI-1) and organization (NPI-2) providers (CLAUDE.md 3, 41), and
 * combines the {@code addresses} entry of purpose LOCATION with any entries in NPPES's real,
 * documented {@code practiceLocations} field -- a provider's genuine additional practice
 * locations, not a synthetic stand-in (CLAUDE.md 4, docs/provider-data-platform.md).
 */
public final class NppesProviderMapper {

    private NppesProviderMapper() {
    }

    public static Optional<MappedProvider> map(NppesResult result, Set<String> knownTaxonomyCodes) {
        if (result.basic() == null) {
            return Optional.empty();
        }

        ProviderEntityType entityType = "NPI-2".equals(result.enumerationType())
                ? ProviderEntityType.ORGANIZATION
                : ProviderEntityType.INDIVIDUAL;
        if (entityType == ProviderEntityType.ORGANIZATION && isBlank(result.basic().organizationName())) {
            return Optional.empty();
        }
        if (entityType == ProviderEntityType.INDIVIDUAL
                && isBlank(result.basic().firstName())
                && isBlank(result.basic().lastName())) {
            return Optional.empty();
        }

        List<NppesAddress> allLocationAddresses = new ArrayList<>();
        if (result.addresses() != null) {
            result.addresses().stream()
                    .filter(a -> "LOCATION".equalsIgnoreCase(a.addressPurpose()))
                    .forEach(allLocationAddresses::add);
        }
        if (result.practiceLocations() != null) {
            allLocationAddresses.addAll(result.practiceLocations());
        }
        if (allLocationAddresses.isEmpty()) {
            return Optional.empty();
        }

        List<NppesTaxonomy> matchingTaxonomies = result.taxonomies() == null
                ? List.of()
                : result.taxonomies().stream()
                        .filter(t -> knownTaxonomyCodes.contains(t.code()))
                        .collect(Collectors.toList());
        if (matchingTaxonomies.isEmpty()) {
            return Optional.empty();
        }

        // De-dup exact duplicate addresses within this single record (e.g. the same office
        // listed in both `addresses` and `practiceLocations`) before returning -- real dedup
        // across imports still happens via the normalized-key upsert, this just avoids counting
        // the same office twice within one source record.
        Map<String, MappedLocation> byKey = new LinkedHashMap<>();
        for (NppesAddress address : allLocationAddresses) {
            MappedLocation location = new MappedLocation(
                    address.address1(), address.address2(), address.city(), address.state(),
                    normalizePostalCode(address.postalCode()), address.telephoneNumber(), address.faxNumber());
            byKey.putIfAbsent(location.dedupKey(), location);
        }

        ProviderIdentityRecord identity = new ProviderIdentityRecord(
                result.number(), entityType, result.basic().firstName(), result.basic().lastName(), result.basic().organizationName());
        List<ProviderTaxonomyRecord> taxonomyRecords =
                matchingTaxonomies.stream().map(t -> new ProviderTaxonomyRecord(t.code(), t.primary())).toList();

        return Optional.of(new MappedProvider(identity, List.copyOf(byKey.values()), taxonomyRecords));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    static String normalizePostalCode(String postalCode) {
        if (postalCode == null) {
            return null;
        }
        return postalCode.length() > 5 ? postalCode.substring(0, 5) : postalCode;
    }

    public record MappedProvider(
            ProviderIdentityRecord identity, List<MappedLocation> locations, List<ProviderTaxonomyRecord> taxonomies) {
    }

    public record MappedLocation(
            String addressLine1, String addressLine2, String city, String stateCode, String postalCode, String phone, String fax) {
        String dedupKey() {
            return String.join(
                    "|",
                    String.valueOf(addressLine1),
                    String.valueOf(addressLine2),
                    String.valueOf(city),
                    String.valueOf(stateCode),
                    String.valueOf(postalCode));
        }
    }
}
