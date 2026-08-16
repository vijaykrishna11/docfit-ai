package com.docfitai.backend.provider;

import com.docfitai.backend.provider.dto.ProviderDetailDto;
import com.docfitai.backend.provider.dto.ProviderLocationDto;
import com.docfitai.backend.provider.dto.ProviderTaxonomyDto;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProviderDetailService {

    private static final String TAXONOMY_QUERY =
            """
            SELECT pt.taxonomy_code, pt.primary_taxonomy, nt.classification, nt.specialization, nt.display_name
            FROM provider_taxonomy pt
            JOIN npi_taxonomy nt ON nt.taxonomy_code = pt.taxonomy_code
            WHERE pt.provider_id = ?
            ORDER BY pt.primary_taxonomy DESC, nt.display_name
            """;

    private final ProviderRepository providerRepository;
    private final ProviderLocationRepository providerLocationRepository;
    private final ProviderSearchService providerSearchService;
    private final JdbcTemplate jdbcTemplate;

    public ProviderDetailService(
            ProviderRepository providerRepository,
            ProviderLocationRepository providerLocationRepository,
            ProviderSearchService providerSearchService,
            JdbcTemplate jdbcTemplate) {
        this.providerRepository = providerRepository;
        this.providerLocationRepository = providerLocationRepository;
        this.providerSearchService = providerSearchService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public ProviderDetailDto getById(Long id, String zip, String location, Double lat, Double lng) {
        Provider provider = providerRepository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider not found: " + id));

        List<ProviderLocation> locations = providerLocationRepository.findByProviderIdOrderByPrimaryDescId(id);

        boolean hasOrigin = (zip != null && !zip.isBlank())
                || (location != null && !location.isBlank())
                || (lat != null && lng != null);

        ProviderLocation selected;
        Double distanceMiles = null;
        if (hasOrigin && !locations.isEmpty()) {
            ProviderSearchService.Origin origin = providerSearchService.resolveOrigin(zip, location, lat, lng);
            SelectedLocation nearest = nearestLocation(locations, origin);
            selected = nearest.location();
            distanceMiles = nearest.distanceMiles();
        } else {
            selected = locations.isEmpty() ? null : locations.get(0);
        }

        List<ProviderLocationDto> otherLocations = locations.stream()
                .filter(candidate -> selected == null || !candidate.getId().equals(selected.getId()))
                .map(ProviderDetailService::toLocationDto)
                .toList();

        List<ProviderTaxonomyDto> taxonomies = jdbcTemplate.query(
                TAXONOMY_QUERY,
                (rs, rowNum) -> new ProviderTaxonomyDto(
                        rs.getString("taxonomy_code"),
                        rs.getString("classification"),
                        rs.getString("specialization"),
                        rs.getString("display_name"),
                        rs.getBoolean("primary_taxonomy")),
                id);

        return new ProviderDetailDto(
                provider.getId(),
                provider.getNpiNumber(),
                provider.getEntityType().name(),
                provider.getFirstName(),
                provider.getLastName(),
                provider.getOrganizationName(),
                selected == null ? null : toLocationDto(selected),
                otherLocations,
                distanceMiles,
                taxonomies,
                provider.getImportedAt());
    }

    private SelectedLocation nearestLocation(List<ProviderLocation> locations, ProviderSearchService.Origin origin) {
        List<SelectedLocation> withDistance = new ArrayList<>();
        for (ProviderLocation candidate : locations) {
            if (candidate.getLatitude() == null || candidate.getLongitude() == null) {
                continue;
            }
            double distance = ProviderSearchService.haversineMiles(
                    origin.latitude(), origin.longitude(), candidate.getLatitude().doubleValue(), candidate.getLongitude().doubleValue());
            withDistance.add(new SelectedLocation(candidate, Math.round(distance * 10.0) / 10.0));
        }
        if (withDistance.isEmpty()) {
            // No location has coordinates -- fall back to the primary location with no distance.
            return new SelectedLocation(locations.get(0), null);
        }
        return withDistance.stream().min(Comparator.comparingDouble(SelectedLocation::distanceMiles)).orElseThrow();
    }

    private static ProviderLocationDto toLocationDto(ProviderLocation location) {
        return new ProviderLocationDto(
                location.getId(),
                location.getAddressLine1(),
                location.getAddressLine2(),
                location.getCity(),
                location.getStateCode(),
                location.getPostalCode(),
                location.getPhone(),
                location.getLatitude(),
                location.getLongitude(),
                location.getCoordinatePrecision().name());
    }

    private record SelectedLocation(ProviderLocation location, Double distanceMiles) {
    }
}
