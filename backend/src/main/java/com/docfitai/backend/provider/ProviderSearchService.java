package com.docfitai.backend.provider;

import com.docfitai.backend.provider.dto.ProviderSearchResponseDto;
import com.docfitai.backend.provider.dto.ProviderSearchResultDto;
import com.docfitai.backend.reference.Specialty;
import com.docfitai.backend.reference.SpecialtyRepository;
import com.docfitai.backend.reference.ZipGeography;
import com.docfitai.backend.reference.ZipGeographyRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProviderSearchService {

    static final double EARTH_RADIUS_MILES = 3958.8;
    private static final String SORT_NAME = "name";
    private static final String SORT_NAME_DESC = "name-desc";

    private static final String MATCH_QUERY =
            """
            SELECT p.id, p.npi_number, p.first_name, p.last_name, p.organization_name, p.phone,
                   p.address_line_1, p.address_line_2, p.city, p.state_code, p.postal_code,
                   p.latitude, p.longitude, pt.taxonomy_code, pt.primary_taxonomy, nt.display_name
            FROM provider p
            JOIN provider_taxonomy pt ON pt.provider_id = p.id
            JOIN npi_taxonomy nt ON nt.taxonomy_code = pt.taxonomy_code
            WHERE pt.taxonomy_code IN (:taxonomyCodes)
              AND p.latitude IS NOT NULL AND p.longitude IS NOT NULL
            """;

    private final SpecialtyRepository specialtyRepository;
    private final ZipGeographyRepository zipGeographyRepository;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ProviderSearchService(
            SpecialtyRepository specialtyRepository,
            ZipGeographyRepository zipGeographyRepository,
            NamedParameterJdbcTemplate jdbcTemplate) {
        this.specialtyRepository = specialtyRepository;
        this.zipGeographyRepository = zipGeographyRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public ProviderSearchResponseDto search(ProviderSearchQuery query) {
        int safePage = Math.max(query.page(), 0);
        int safeSize = query.size() <= 0 ? 20 : query.size();
        double radiusMiles = query.radiusMiles() <= 0 ? 25 : query.radiusMiles();

        Specialty specialty = specialtyRepository
                .findByCode(query.specialtyCode())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Unknown specialty code: " + query.specialtyCode()));

        Origin origin = resolveOrigin(query.zip(), query.location(), query.lat(), query.lng());

        List<String> taxonomyCodes = jdbcTemplate
                .getJdbcTemplate()
                .queryForList(
                        "SELECT taxonomy_code FROM specialty_taxonomy_mapping WHERE specialty_id = ?",
                        String.class,
                        specialty.getId());
        if (taxonomyCodes.isEmpty()) {
            return new ProviderSearchResponseDto(List.of(), safePage, safeSize, 0, 0, origin.label());
        }

        Map<Long, MatchRow> bestMatchByProvider = new LinkedHashMap<>();
        MapSqlParameterSource params = new MapSqlParameterSource("taxonomyCodes", taxonomyCodes);
        jdbcTemplate.query(MATCH_QUERY, params, rs -> {
            long providerId = rs.getLong("id");
            boolean primary = rs.getBoolean("primary_taxonomy");
            MatchRow existing = bestMatchByProvider.get(providerId);
            if (existing == null || (primary && !existing.primaryTaxonomy())) {
                bestMatchByProvider.put(
                        providerId,
                        new MatchRow(
                                providerId,
                                rs.getString("npi_number"),
                                rs.getString("first_name"),
                                rs.getString("last_name"),
                                rs.getString("organization_name"),
                                rs.getString("phone"),
                                rs.getString("address_line_1"),
                                rs.getString("address_line_2"),
                                rs.getString("city"),
                                rs.getString("state_code"),
                                rs.getString("postal_code"),
                                rs.getBigDecimal("latitude"),
                                rs.getBigDecimal("longitude"),
                                rs.getString("taxonomy_code"),
                                rs.getString("display_name"),
                                primary));
            }
        });

        List<ProviderSearchResultDto> withinRadius = new ArrayList<>();
        for (MatchRow row : bestMatchByProvider.values()) {
            double distance = haversineMiles(
                    origin.latitude(), origin.longitude(), row.latitude().doubleValue(), row.longitude().doubleValue());
            if (distance <= radiusMiles) {
                withinRadius.add(new ProviderSearchResultDto(
                        row.providerId(),
                        row.npiNumber(),
                        row.firstName(),
                        row.lastName(),
                        row.organizationName(),
                        row.phone(),
                        row.addressLine1(),
                        row.addressLine2(),
                        row.city(),
                        row.stateCode(),
                        row.postalCode(),
                        row.taxonomyCode(),
                        row.taxonomyDisplayName(),
                        Math.round(distance * 10.0) / 10.0));
            }
        }

        Comparator<ProviderSearchResultDto> nameComparator =
                Comparator.comparing(ProviderSearchService::displayName, String.CASE_INSENSITIVE_ORDER);
        if (SORT_NAME.equalsIgnoreCase(query.sort())) {
            withinRadius.sort(nameComparator);
        } else if (SORT_NAME_DESC.equalsIgnoreCase(query.sort())) {
            withinRadius.sort(nameComparator.reversed());
        } else {
            withinRadius.sort(Comparator.comparingDouble(ProviderSearchResultDto::distanceMiles));
        }

        int totalElements = withinRadius.size();
        int totalPages = (int) Math.ceil(totalElements / (double) safeSize);
        int fromIndex = Math.min(safePage * safeSize, totalElements);
        int toIndex = Math.min(fromIndex + safeSize, totalElements);
        List<ProviderSearchResultDto> pageResults = withinRadius.subList(fromIndex, toIndex);

        return new ProviderSearchResponseDto(pageResults, safePage, safeSize, totalElements, totalPages, origin.label());
    }

    private static String displayName(ProviderSearchResultDto dto) {
        if (dto.organizationName() != null && !dto.organizationName().isBlank()) {
            return dto.organizationName();
        }
        String first = dto.firstName() == null ? "" : dto.firstName();
        String last = dto.lastName() == null ? "" : dto.lastName();
        return (first + " " + last).trim();
    }

    /**
     * Resolves the search origin with precedence lat/lng &gt; zip &gt; free-text location.
     * Free-text location is either a 5-digit ZIP or a city name (optionally "City, ST"),
     * matched against the local {@code zip_geography} reference data -- no external geocoding.
     */
    Origin resolveOrigin(String zip, String location, Double lat, Double lng) {
        if (lat != null && lng != null) {
            return new Origin(lat, lng, null);
        }
        if (zip != null && !zip.isBlank()) {
            return originFromZip(zip.trim());
        }
        if (location != null && !location.isBlank()) {
            String trimmed = location.trim();
            if (trimmed.matches("\\d{5}")) {
                return originFromZip(trimmed);
            }
            return originFromCity(trimmed);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A ZIP code or location is required");
    }

    private Origin originFromZip(String zip) {
        ZipGeography zipGeography = zipGeographyRepository
                .findById(zip)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown ZIP code: " + zip));
        return new Origin(
                zipGeography.getLatitude().doubleValue(),
                zipGeography.getLongitude().doubleValue(),
                zipGeography.getCity() + ", " + zipGeography.getStateCode());
    }

    private Origin originFromCity(String location) {
        String cityPart = location.split(",")[0].trim();
        List<ZipGeography> matches = zipGeographyRepository.findAll().stream()
                .filter(zipGeography -> zipGeography.getCity().equalsIgnoreCase(cityPart))
                .toList();
        if (matches.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown location: " + location);
        }
        double avgLat =
                matches.stream().mapToDouble(z -> z.getLatitude().doubleValue()).average().orElseThrow();
        double avgLon =
                matches.stream().mapToDouble(z -> z.getLongitude().doubleValue()).average().orElseThrow();
        return new Origin(avgLat, avgLon, matches.get(0).getCity() + ", " + matches.get(0).getStateCode());
    }

    static double haversineMiles(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_MILES * c;
    }

    /** Resolved search origin. {@code label} is null when the origin came from raw lat/lng (e.g. geolocation). */
    record Origin(double latitude, double longitude, String label) {
    }

    private record MatchRow(
            long providerId,
            String npiNumber,
            String firstName,
            String lastName,
            String organizationName,
            String phone,
            String addressLine1,
            String addressLine2,
            String city,
            String stateCode,
            String postalCode,
            BigDecimal latitude,
            BigDecimal longitude,
            String taxonomyCode,
            String taxonomyDisplayName,
            boolean primaryTaxonomy) {
    }
}
