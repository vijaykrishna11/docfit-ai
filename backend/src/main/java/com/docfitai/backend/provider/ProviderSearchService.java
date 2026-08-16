package com.docfitai.backend.provider;

import com.docfitai.backend.insurance.dto.NetworkEvidenceSummaryDto;
import com.docfitai.backend.insurance.evidence.NetworkEvidenceService;
import com.docfitai.backend.provider.dto.ProviderLocationDto;
import com.docfitai.backend.provider.dto.ProviderSearchResponseDto;
import com.docfitai.backend.provider.dto.ProviderSearchResultDto;
import com.docfitai.backend.reference.Specialty;
import com.docfitai.backend.reference.SpecialtyRepository;
import com.docfitai.backend.reference.ZipGeography;
import com.docfitai.backend.reference.ZipGeographyRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * A provider can have multiple practice locations and multiple matching taxonomies (CLAUDE.md
 * 2, 10-11). Search must still return each qualifying provider exactly once, attached to the
 * single nearest location that falls within the requested radius -- never once per office.
 * Taxonomy selection (primary preferred) and location selection (nearest within radius) are
 * independent choices, resolved together per provider from one query result set.
 */
@Service
public class ProviderSearchService {

    static final double EARTH_RADIUS_MILES = 3958.8;
    private static final String SORT_NAME = "name";
    private static final String SORT_NAME_DESC = "name-desc";
    // Upper bounds only -- the existing "<= 0 -> default" clamps below already cover the lower
    // end. Without these, a client-supplied radius/size has no ceiling: radius alone doesn't
    // bound MATCH_QUERY's cost (see the bounding-box params added below, which do), and size
    // alone controls how large a single response payload gets built and serialized.
    static final double MAX_RADIUS_MILES = 250;
    static final int MAX_PAGE_SIZE = 200;
    // Approximate miles per degree of latitude; used only to size a superset bounding box for
    // MATCH_QUERY, never to compute the actual distance shown to the user (that's still the
    // precise Haversine calculation below). A generous approximation is fine because the exact
    // circle filter (radiusMiles) is re-applied in Java after this query returns.
    private static final double MILES_PER_DEGREE_LATITUDE = 69.0;
    // Matches CoordinatePrecision.EXACT/ADDRESS_GEOCODE -- a real geocode, not a ZIP/city centroid
    // approximation. Kept as plain strings (not the enum) since this DTO field is already a String.
    private static final Set<String> PRECISE_COORDINATE_PRECISIONS = Set.of("EXACT", "ADDRESS_GEOCODE");

    private static final String MATCH_QUERY =
            """
            SELECT p.id, p.npi_number, p.entity_type, p.first_name, p.last_name, p.organization_name,
                   pt.taxonomy_code, pt.primary_taxonomy, nt.display_name,
                   pl.id AS location_id, pl.address_line_1, pl.address_line_2, pl.city, pl.state_code,
                   pl.postal_code, pl.phone, pl.latitude, pl.longitude, pl.coordinate_precision
            FROM provider p
            JOIN provider_taxonomy pt ON pt.provider_id = p.id
            JOIN npi_taxonomy nt ON nt.taxonomy_code = pt.taxonomy_code
            JOIN provider_location pl ON pl.provider_id = p.id AND pl.address_purpose = 'LOCATION'
            WHERE pt.taxonomy_code IN (:taxonomyCodes)
              AND pl.latitude IS NOT NULL AND pl.longitude IS NOT NULL
              AND pl.latitude BETWEEN :minLat AND :maxLat
              AND pl.longitude BETWEEN :minLng AND :maxLng
            """;

    private final SpecialtyRepository specialtyRepository;
    private final ZipGeographyRepository zipGeographyRepository;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final NetworkEvidenceService networkEvidenceService;

    public ProviderSearchService(
            SpecialtyRepository specialtyRepository,
            ZipGeographyRepository zipGeographyRepository,
            NamedParameterJdbcTemplate jdbcTemplate,
            NetworkEvidenceService networkEvidenceService) {
        this.specialtyRepository = specialtyRepository;
        this.zipGeographyRepository = zipGeographyRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.networkEvidenceService = networkEvidenceService;
    }

    public ProviderSearchResponseDto search(ProviderSearchQuery query) {
        int safePage = Math.max(query.page(), 0);
        int safeSize = query.size() <= 0 ? 20 : Math.min(query.size(), MAX_PAGE_SIZE);
        double radiusMiles = query.radiusMiles() <= 0 ? 25 : Math.min(query.radiusMiles(), MAX_RADIUS_MILES);

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

        // One entry per provider: identity fields, the best (primary-preferred) matching
        // taxonomy, and the nearest qualifying location seen so far.
        Map<Long, ProviderMatch> matchByProvider = new LinkedHashMap<>();
        // Superset bounding box so MATCH_QUERY doesn't have to load every matching-specialty
        // provider nationwide before filtering by distance in Java -- without this, a common
        // specialty (e.g. family medicine, ~200k+ NPIs nationally) would pull its entire
        // nationwide result set into memory on every search, regardless of radius. The exact
        // circle (radiusMiles) is still enforced below via Haversine; this box only has to be a
        // superset, not exact.
        double latDeltaDegrees = radiusMiles / MILES_PER_DEGREE_LATITUDE;
        double milesPerDegreeLongitude =
                MILES_PER_DEGREE_LATITUDE * Math.max(Math.cos(Math.toRadians(origin.latitude())), 0.01);
        double lngDeltaDegrees = radiusMiles / milesPerDegreeLongitude;
        MapSqlParameterSource params = new MapSqlParameterSource("taxonomyCodes", taxonomyCodes)
                .addValue("minLat", origin.latitude() - latDeltaDegrees)
                .addValue("maxLat", origin.latitude() + latDeltaDegrees)
                .addValue("minLng", origin.longitude() - lngDeltaDegrees)
                .addValue("maxLng", origin.longitude() + lngDeltaDegrees);
        jdbcTemplate.query(MATCH_QUERY, params, rs -> {
            long providerId = rs.getLong("id");
            double distance = haversineMiles(
                    origin.latitude(), origin.longitude(), rs.getBigDecimal("latitude").doubleValue(),
                    rs.getBigDecimal("longitude").doubleValue());

            ProviderMatch existing = matchByProvider.get(providerId);
            if (existing == null) {
                existing = new ProviderMatch(
                        providerId,
                        rs.getString("npi_number"),
                        rs.getString("entity_type"),
                        rs.getString("first_name"),
                        rs.getString("last_name"),
                        rs.getString("organization_name"));
                matchByProvider.put(providerId, existing);
            }
            existing.considerTaxonomy(rs.getString("taxonomy_code"), rs.getString("display_name"), rs.getBoolean("primary_taxonomy"));
            existing.considerLocation(
                    rs.getLong("location_id"),
                    rs.getString("address_line_1"),
                    rs.getString("address_line_2"),
                    rs.getString("city"),
                    rs.getString("state_code"),
                    rs.getString("postal_code"),
                    rs.getString("phone"),
                    rs.getBigDecimal("latitude"),
                    rs.getBigDecimal("longitude"),
                    rs.getString("coordinate_precision"),
                    distance);
        });

        List<ProviderSearchResultDto> withinRadius = new ArrayList<>();
        for (ProviderMatch match : matchByProvider.values()) {
            if (match.nearestDistance == null || match.nearestDistance > radiusMiles) {
                continue;
            }
            withinRadius.add(new ProviderSearchResultDto(
                    match.providerId,
                    match.npiNumber,
                    match.entityType,
                    match.firstName,
                    match.lastName,
                    match.organizationName,
                    match.taxonomyCode,
                    match.taxonomyDisplayName,
                    match.nearestLocation,
                    Math.round(match.nearestDistance * 10.0) / 10.0));
        }

        FilterResult filtered = applyPracticalFitFilters(withinRadius, query);
        // Filters that actually ran return an immutable List (Stream.toList()) -- re-wrap in a
        // mutable list unconditionally so the in-place .sort() below never throws
        // UnsupportedOperationException, regardless of which filters (if any) were applied.
        withinRadius = new ArrayList<>(filtered.results());

        Comparator<ProviderSearchResultDto> nameComparator =
                Comparator.comparing(ProviderSearchService::displayName, String.CASE_INSENSITIVE_ORDER);
        if (SORT_NAME.equalsIgnoreCase(query.sort())) {
            withinRadius.sort(nameComparator);
        } else if (SORT_NAME_DESC.equalsIgnoreCase(query.sort())) {
            withinRadius.sort(nameComparator.reversed());
        } else {
            withinRadius.sort(Comparator.comparingDouble(ProviderSearchResultDto::distanceMiles));
        }

        // Pagination counts providers, not location rows (CLAUDE.md 47) -- withinRadius already
        // has exactly one entry per qualifying provider by construction.
        int totalElements = withinRadius.size();
        int totalPages = (int) Math.ceil(totalElements / (double) safeSize);
        int fromIndex = Math.min(safePage * safeSize, totalElements);
        int toIndex = Math.min(fromIndex + safeSize, totalElements);
        List<ProviderSearchResultDto> pageResults = withinRadius.subList(fromIndex, toIndex);
        pageResults = attachNetworkEvidence(pageResults, query.planId(), filtered.evidenceByProvider());

        return new ProviderSearchResponseDto(pageResults, safePage, safeSize, totalElements, totalPages, origin.label());
    }

    /**
     * Batched, single-query evidence lookup for the page actually being returned -- never one
     * lookup per provider (CLAUDE.md 89-91). Evidence is looked up against the specific location
     * each result is showing (CLAUDE.md 9). Omitting {@code planId} leaves every result's
     * networkEvidence field null rather than a fabricated status.
     *
     * @param alreadyComputed when the {@code networkEvidenceFound} filter was applied, evidence for
     *     every surviving candidate was already fetched in one batch to decide who qualifies --
     *     reused here instead of a second batched lookup for the same providers.
     */
    private List<ProviderSearchResultDto> attachNetworkEvidence(
            List<ProviderSearchResultDto> pageResults, Long planId, Map<Long, NetworkEvidenceSummaryDto> alreadyComputed) {
        if (planId == null || pageResults.isEmpty()) {
            return pageResults;
        }
        Map<Long, NetworkEvidenceSummaryDto> evidenceByProvider = alreadyComputed;
        if (evidenceByProvider == null) {
            Map<Long, Long> providerLocations = new HashMap<>();
            for (ProviderSearchResultDto result : pageResults) {
                providerLocations.put(result.id(), result.location() == null ? null : result.location().id());
            }
            evidenceByProvider = networkEvidenceService.summarizeForProviders(providerLocations, planId);
        }
        Map<Long, NetworkEvidenceSummaryDto> finalEvidenceByProvider = evidenceByProvider;
        return pageResults.stream()
                .map(result -> result.withNetworkEvidence(finalEvidenceByProvider.get(result.id())))
                .toList();
    }

    /**
     * Applies the optional practical-fit filters (CLAUDE.md "Practical Fit Filter Bar") to the
     * within-radius candidate set, before sorting/pagination. providerType/hasPhone/
     * preciseLocationOnly are pure in-memory checks against data already fetched by MATCH_QUERY --
     * no extra queries. multipleLocations and networkEvidenceFound each need one additional
     * batched query (never one per provider) over the remaining candidates at that point.
     */
    private FilterResult applyPracticalFitFilters(List<ProviderSearchResultDto> candidates, ProviderSearchQuery query) {
        if (query.providerType() != null) {
            String wanted = query.providerType().toUpperCase(Locale.ROOT);
            candidates = candidates.stream().filter(c -> wanted.equals(c.entityType())).toList();
        }
        if (Boolean.TRUE.equals(query.hasPhone())) {
            candidates = candidates.stream()
                    .filter(c -> c.location() != null && c.location().phone() != null && !c.location().phone().isBlank())
                    .toList();
        }
        if (Boolean.TRUE.equals(query.preciseLocationOnly())) {
            candidates = candidates.stream()
                    .filter(c -> c.location() != null && PRECISE_COORDINATE_PRECISIONS.contains(c.location().coordinatePrecision()))
                    .toList();
        }
        if (Boolean.TRUE.equals(query.multipleLocations()) && !candidates.isEmpty()) {
            List<Long> candidateIds = candidates.stream().map(ProviderSearchResultDto::id).toList();
            List<Long> multiLocationProviderIds = jdbcTemplate.query(
                    "SELECT provider_id FROM provider_location WHERE provider_id IN (:ids) "
                            + "AND address_purpose = 'LOCATION' GROUP BY provider_id HAVING COUNT(*) > 1",
                    new MapSqlParameterSource("ids", candidateIds),
                    (rs, rowNum) -> rs.getLong("provider_id"));
            Set<Long> multiLocationSet = new HashSet<>(multiLocationProviderIds);
            candidates = candidates.stream().filter(c -> multiLocationSet.contains(c.id())).toList();
        }
        Map<Long, NetworkEvidenceSummaryDto> evidenceByProvider = null;
        if (Boolean.TRUE.equals(query.networkEvidenceFound())) {
            if (query.planId() == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "networkEvidenceFound filter requires a planId.");
            }
            Map<Long, Long> providerLocations = new HashMap<>();
            for (ProviderSearchResultDto c : candidates) {
                providerLocations.put(c.id(), c.location() == null ? null : c.location().id());
            }
            evidenceByProvider = candidates.isEmpty()
                    ? Map.of()
                    : networkEvidenceService.summarizeForProviders(providerLocations, query.planId());
            Map<Long, NetworkEvidenceSummaryDto> finalEvidenceByProvider = evidenceByProvider;
            candidates = candidates.stream()
                    .filter(c -> {
                        NetworkEvidenceSummaryDto evidence = finalEvidenceByProvider.get(c.id());
                        return evidence != null && "EVIDENCE_FOUND".equals(evidence.status());
                    })
                    .toList();
        }
        return new FilterResult(candidates, evidenceByProvider);
    }

    private record FilterResult(List<ProviderSearchResultDto> results, Map<Long, NetworkEvidenceSummaryDto> evidenceByProvider) {
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

    /** Accumulates the best taxonomy match and nearest qualifying location seen for one provider across the result set. */
    private static final class ProviderMatch {
        final long providerId;
        final String npiNumber;
        final String entityType;
        final String firstName;
        final String lastName;
        final String organizationName;

        String taxonomyCode;
        String taxonomyDisplayName;
        boolean primaryTaxonomy;

        ProviderLocationDto nearestLocation;
        Double nearestDistance;

        ProviderMatch(long providerId, String npiNumber, String entityType, String firstName, String lastName, String organizationName) {
            this.providerId = providerId;
            this.npiNumber = npiNumber;
            this.entityType = entityType;
            this.firstName = firstName;
            this.lastName = lastName;
            this.organizationName = organizationName;
        }

        void considerTaxonomy(String code, String displayName, boolean primary) {
            if (taxonomyCode == null || (primary && !primaryTaxonomy)) {
                taxonomyCode = code;
                taxonomyDisplayName = displayName;
                primaryTaxonomy = primary;
            }
        }

        void considerLocation(
                long locationId,
                String addressLine1,
                String addressLine2,
                String city,
                String stateCode,
                String postalCode,
                String phone,
                BigDecimal latitude,
                BigDecimal longitude,
                String coordinatePrecision,
                double distance) {
            if (nearestDistance == null || distance < nearestDistance) {
                nearestDistance = distance;
                nearestLocation = new ProviderLocationDto(
                        locationId, addressLine1, addressLine2, city, stateCode, postalCode, phone, latitude, longitude,
                        coordinatePrecision, Math.round(distance * 10.0) / 10.0);
            }
        }
    }
}
