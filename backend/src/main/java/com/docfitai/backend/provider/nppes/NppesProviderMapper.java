package com.docfitai.backend.provider.nppes;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pure mapping/filtering logic from a raw NPPES search result to the fields DocFit AI persists.
 * Kept side-effect free (no network, no Spring context) so it can be unit tested directly.
 */
public final class NppesProviderMapper {

    private NppesProviderMapper() {
    }

    public static Optional<MappedProvider> map(NppesResult result, Set<String> knownTaxonomyCodes) {
        if (result.basic() == null) {
            return Optional.empty();
        }

        NppesAddress location = result.addresses() == null
                ? null
                : result.addresses().stream()
                        .filter(a -> "LOCATION".equalsIgnoreCase(a.addressPurpose()))
                        .findFirst()
                        .orElse(null);
        if (location == null) {
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

        return Optional.of(new MappedProvider(
                result.number(),
                result.basic().firstName(),
                result.basic().lastName(),
                location.address1(),
                location.address2(),
                location.city(),
                location.state(),
                normalizePostalCode(location.postalCode()),
                location.telephoneNumber(),
                matchingTaxonomies));
    }

    static String normalizePostalCode(String postalCode) {
        if (postalCode == null) {
            return null;
        }
        return postalCode.length() > 5 ? postalCode.substring(0, 5) : postalCode;
    }

    public record MappedProvider(
            String npiNumber,
            String firstName,
            String lastName,
            String addressLine1,
            String addressLine2,
            String city,
            String stateCode,
            String postalCode,
            String phone,
            List<NppesTaxonomy> matchingTaxonomies) {
    }
}
