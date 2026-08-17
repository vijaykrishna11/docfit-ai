package com.docfitai.backend.provider.nppes;

import com.docfitai.backend.provider.CoordinatePrecision;
import com.docfitai.backend.provider.ingestion.ProviderImportRecord;
import com.docfitai.backend.provider.ingestion.ProviderLocationRecord;
import com.docfitai.backend.reference.ZipGeography;
import com.docfitai.backend.reference.ZipGeographyRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Turns a mapped NPPES provider into a {@link ProviderImportRecord}, enriching each location with
 * a ZIP-centroid coordinate where the ZIP is known (CLAUDE.md "Geocoding" -- always truthfully
 * labeled {@code ZIP_CENTROID}, never a real address geocode, and never done here for a ZIP that
 * isn't already in {@code zip_geography}). Shared by every NPPES-based importer/refresher
 * ({@link NppesImportRunner}, the operator-triggered refresh path) so this enrichment logic exists
 * in exactly one place.
 */
@Component
public class NppesRecordFactory {

    private final ZipGeographyRepository zipGeographyRepository;

    public NppesRecordFactory(ZipGeographyRepository zipGeographyRepository) {
        this.zipGeographyRepository = zipGeographyRepository;
    }

    public ProviderImportRecord toImportRecord(NppesProviderMapper.MappedProvider mapped) {
        List<ProviderLocationRecord> locationRecords = mapped.locations().stream()
                .map(location -> {
                    BigDecimal latitude = null;
                    BigDecimal longitude = null;
                    CoordinatePrecision precision = CoordinatePrecision.UNKNOWN;
                    Optional<ZipGeography> coordZip = zipGeographyRepository.findById(location.postalCode());
                    if (coordZip.isPresent()) {
                        latitude = coordZip.get().getLatitude();
                        longitude = coordZip.get().getLongitude();
                        precision = CoordinatePrecision.ZIP_CENTROID;
                    }
                    return new ProviderLocationRecord(
                            "LOCATION",
                            location.addressLine1(),
                            location.addressLine2(),
                            location.city(),
                            location.stateCode(),
                            location.postalCode(),
                            location.phone(),
                            location.fax(),
                            latitude,
                            longitude,
                            precision);
                })
                .toList();
        return new ProviderImportRecord(mapped.identity(), locationRecords, mapped.taxonomies());
    }
}
