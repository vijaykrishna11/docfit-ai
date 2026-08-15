package com.docfitai.backend.reference;

import com.docfitai.backend.reference.dto.LocationSuggestionDto;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Suggests locations from the local {@code zip_geography} reference data only -- no external
 * geocoding or paid location service. The demo dataset is intentionally small (a handful of
 * Long Beach / nearby LA ZIPs), so results are naturally capped.
 */
@Service
public class LocationSuggestionService {

    private static final int MAX_RESULTS = 8;

    private final ZipGeographyRepository zipGeographyRepository;

    public LocationSuggestionService(ZipGeographyRepository zipGeographyRepository) {
        this.zipGeographyRepository = zipGeographyRepository;
    }

    public List<LocationSuggestionDto> suggest(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase();

        return zipGeographyRepository.findAll().stream()
                .filter(zip -> normalized.isEmpty()
                        || zip.getCity().toLowerCase().contains(normalized)
                        || zip.getZipCode().startsWith(normalized))
                .sorted(Comparator.comparing(ZipGeography::getCity).thenComparing(ZipGeography::getZipCode))
                .limit(MAX_RESULTS)
                .map(zip -> new LocationSuggestionDto(
                        zip.getZipCode(),
                        zip.getCity(),
                        zip.getStateCode(),
                        zip.getZipCode() + " — " + zip.getCity() + ", " + zip.getStateCode()))
                .toList();
    }
}
