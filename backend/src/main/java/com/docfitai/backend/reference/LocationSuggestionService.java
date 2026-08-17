package com.docfitai.backend.reference;

import com.docfitai.backend.reference.dto.LocationSuggestionDto;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Suggests locations from the local {@code zip_geography} reference data only -- no external
 * geocoding or paid location service (CLAUDE.md "Location Suggestions V3").
 *
 * <p>Two kinds of suggestion, distinguished by {@link LocationSuggestionDto#type()}:
 * <ul>
 *   <li>{@code ZIP} -- one specific ZIP row.
 *   <li>{@code CITY} -- one entry per distinct city/state, deduplicated across however many ZIPs
 *       share that city (never one suggestion per ZIP within the same city -- with 295 real LA
 *       County ZIPs now loaded, several cities span a dozen+ ZIPs).
 * </ul>
 *
 * <p>Ranked (highest first): exact ZIP match, exact city-prefix match, ZIP-prefix match (excluding
 * the exact match already surfaced), then city-contains-anywhere as the weakest tier. Bounded to
 * {@link #MAX_RESULTS}.
 */
@Service
public class LocationSuggestionService {

    private static final int MAX_RESULTS = 8;

    private final ZipGeographyRepository zipGeographyRepository;

    public LocationSuggestionService(ZipGeographyRepository zipGeographyRepository) {
        this.zipGeographyRepository = zipGeographyRepository;
    }

    public List<LocationSuggestionDto> suggest(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<ZipGeography> all = zipGeographyRepository.findAll();
        List<CityGroup> cities = dedupedCities(all);

        if (normalized.isEmpty()) {
            return cities.stream()
                    .sorted(Comparator.comparing(CityGroup::city))
                    .limit(MAX_RESULTS)
                    .map(this::toCitySuggestion)
                    .toList();
        }

        boolean digitsOnly = !normalized.isEmpty() && normalized.chars().allMatch(Character::isDigit);
        Map<String, LocationSuggestionDto> ranked = new LinkedHashMap<>();

        // Tier 0: exact ZIP match.
        all.stream()
                .filter(z -> z.getZipCode().equals(normalized))
                .map(this::toZipSuggestion)
                .forEach(s -> ranked.putIfAbsent(dedupKey(s), s));

        // Tier 1: exact city-prefix match.
        cities.stream()
                .filter(c -> c.city().toLowerCase(Locale.ROOT).startsWith(normalized))
                .sorted(Comparator.comparing(CityGroup::city))
                .map(this::toCitySuggestion)
                .forEach(s -> ranked.putIfAbsent(dedupKey(s), s));

        // Tier 2: ZIP-prefix match (excludes the exact match already added in tier 0).
        if (digitsOnly) {
            all.stream()
                    .filter(z -> z.getZipCode().startsWith(normalized) && !z.getZipCode().equals(normalized))
                    .sorted(Comparator.comparing(ZipGeography::getZipCode))
                    .map(this::toZipSuggestion)
                    .forEach(s -> ranked.putIfAbsent(dedupKey(s), s));
        }

        // Tier 3: weaker match -- city contains the query anywhere, not just as a prefix.
        cities.stream()
                .filter(c -> {
                    String lower = c.city().toLowerCase(Locale.ROOT);
                    return lower.contains(normalized) && !lower.startsWith(normalized);
                })
                .sorted(Comparator.comparing(CityGroup::city))
                .map(this::toCitySuggestion)
                .forEach(s -> ranked.putIfAbsent(dedupKey(s), s));

        return ranked.values().stream().limit(MAX_RESULTS).toList();
    }

    /** One representative row per distinct (city, state) -- never null-city rows (CLAUDE.md "City Representation Limitations"). */
    private List<CityGroup> dedupedCities(List<ZipGeography> all) {
        Map<String, CityGroup> byKey = new LinkedHashMap<>();
        for (ZipGeography zip : all) {
            if (zip.getCity() == null) {
                continue;
            }
            String key = zip.getCity().toLowerCase(Locale.ROOT) + "|" + zip.getStateCode();
            byKey.putIfAbsent(key, new CityGroup(zip.getCity(), zip.getStateCode()));
        }
        return List.copyOf(byKey.values());
    }

    private LocationSuggestionDto toZipSuggestion(ZipGeography zip) {
        String label = zip.getCity() != null
                ? zip.getZipCode() + " — " + zip.getCity() + ", " + zip.getStateCode()
                : zip.getZipCode() + ", " + zip.getStateCode();
        return new LocationSuggestionDto(zip.getZipCode(), zip.getCity(), zip.getStateCode(), label, LocationSuggestionDto.TYPE_ZIP);
    }

    private LocationSuggestionDto toCitySuggestion(CityGroup city) {
        return new LocationSuggestionDto(null, city.city(), city.stateCode(), city.city() + ", " + city.stateCode(), LocationSuggestionDto.TYPE_CITY);
    }

    private static String dedupKey(LocationSuggestionDto suggestion) {
        return suggestion.type().equals(LocationSuggestionDto.TYPE_ZIP)
                ? "ZIP|" + suggestion.zipCode()
                : "CITY|" + suggestion.city().toLowerCase(Locale.ROOT) + "|" + suggestion.stateCode();
    }

    private record CityGroup(String city, String stateCode) {
    }
}
