package com.docfitai.backend.provider;

import com.docfitai.backend.provider.dto.ProviderSearchResponseDto;
import com.docfitai.backend.provider.dto.ProviderSearchResultDto;
import com.docfitai.backend.reference.Specialty;
import com.docfitai.backend.reference.SpecialtyRepository;
import com.docfitai.backend.reference.ZipGeography;
import com.docfitai.backend.reference.ZipGeographyRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
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

    private static final double EARTH_RADIUS_MILES = 3958.8;

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

    public ProviderSearchResponseDto search(String specialtyCode, String zip, double radiusMiles, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 20 : size;

        Specialty specialty = specialtyRepository
                .findByCode(specialtyCode)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Unknown specialty code: " + specialtyCode));

        List<String> taxonomyCodes = jdbcTemplate
                .getJdbcTemplate()
                .queryForList(
                        "SELECT taxonomy_code FROM specialty_taxonomy_mapping WHERE specialty_id = ?",
                        String.class,
                        specialty.getId());
        if (taxonomyCodes.isEmpty()) {
            return new ProviderSearchResponseDto(List.of(), safePage, safeSize, 0, 0);
        }

        ZipGeography origin = zipGeographyRepository
                .findById(zip)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown ZIP code: " + zip));
        double originLat = origin.getLatitude().doubleValue();
        double originLon = origin.getLongitude().doubleValue();

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
                    originLat, originLon, row.latitude().doubleValue(), row.longitude().doubleValue());
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
        withinRadius.sort((a, b) -> Double.compare(a.distanceMiles(), b.distanceMiles()));

        int totalElements = withinRadius.size();
        int totalPages = (int) Math.ceil(totalElements / (double) safeSize);
        int fromIndex = Math.min(safePage * safeSize, totalElements);
        int toIndex = Math.min(fromIndex + safeSize, totalElements);
        List<ProviderSearchResultDto> pageResults = withinRadius.subList(fromIndex, toIndex);

        return new ProviderSearchResponseDto(pageResults, safePage, safeSize, totalElements, totalPages);
    }

    static double haversineMiles(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_MILES * c;
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
