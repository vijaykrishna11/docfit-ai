package com.docfitai.backend.provider;

import com.docfitai.backend.provider.dto.ProviderDetailDto;
import com.docfitai.backend.provider.dto.ProviderTaxonomyDto;
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
    private final ProviderSearchService providerSearchService;
    private final JdbcTemplate jdbcTemplate;

    public ProviderDetailService(
            ProviderRepository providerRepository,
            ProviderSearchService providerSearchService,
            JdbcTemplate jdbcTemplate) {
        this.providerRepository = providerRepository;
        this.providerSearchService = providerSearchService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public ProviderDetailDto getById(Long id, String zip, String location, Double lat, Double lng) {
        Provider provider = providerRepository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider not found: " + id));

        Double distanceMiles = resolveDistance(provider, zip, location, lat, lng);

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
                provider.getFirstName(),
                provider.getLastName(),
                provider.getOrganizationName(),
                provider.getPhone(),
                provider.getAddressLine1(),
                provider.getAddressLine2(),
                provider.getCity(),
                provider.getStateCode(),
                provider.getPostalCode(),
                distanceMiles,
                taxonomies);
    }

    private Double resolveDistance(Provider provider, String zip, String location, Double lat, Double lng) {
        boolean hasOrigin = (zip != null && !zip.isBlank())
                || (location != null && !location.isBlank())
                || (lat != null && lng != null);
        if (!hasOrigin || provider.getLatitude() == null || provider.getLongitude() == null) {
            return null;
        }

        ProviderSearchService.Origin origin = providerSearchService.resolveOrigin(zip, location, lat, lng);
        double distance = ProviderSearchService.haversineMiles(
                origin.latitude(),
                origin.longitude(),
                provider.getLatitude().doubleValue(),
                provider.getLongitude().doubleValue());
        return Math.round(distance * 10.0) / 10.0;
    }
}
