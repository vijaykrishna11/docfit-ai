package com.docfitai.backend.provider;

import com.docfitai.backend.provider.dto.ProviderDetailDto;
import com.docfitai.backend.provider.dto.ProviderNameSearchResultDto;
import com.docfitai.backend.provider.dto.ProviderSearchResponseDto;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/providers")
public class ProviderController {

    private static final Set<String> ALLOWED_PROVIDER_TYPES = Set.of("INDIVIDUAL", "ORGANIZATION");

    private final ProviderSearchService providerSearchService;
    private final ProviderDetailService providerDetailService;
    private final ProviderNameSearchService providerNameSearchService;

    public ProviderController(
            ProviderSearchService providerSearchService,
            ProviderDetailService providerDetailService,
            ProviderNameSearchService providerNameSearchService) {
        this.providerSearchService = providerSearchService;
        this.providerDetailService = providerDetailService;
        this.providerNameSearchService = providerNameSearchService;
    }

    @GetMapping("/by-name")
    public List<ProviderNameSearchResultDto> searchByName(@RequestParam(defaultValue = "") String q) {
        return providerNameSearchService.search(q);
    }

    @GetMapping("/search")
    public ProviderSearchResponseDto search(
            @RequestParam String specialty,
            @RequestParam(required = false) String zip,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(defaultValue = "25") double radius,
            @RequestParam(defaultValue = "distance") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long planId,
            @RequestParam(required = false) String providerType,
            @RequestParam(required = false) Boolean hasPhone,
            @RequestParam(required = false) Boolean preciseLocationOnly,
            @RequestParam(required = false) Boolean networkEvidenceFound,
            @RequestParam(required = false) Boolean multipleLocations) {
        if (providerType != null && !ALLOWED_PROVIDER_TYPES.contains(providerType.toUpperCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown providerType: " + providerType);
        }
        return providerSearchService.search(new ProviderSearchQuery(
                specialty, zip, location, lat, lng, radius, sort, page, size, planId, providerType, hasPhone,
                preciseLocationOnly, networkEvidenceFound, multipleLocations));
    }

    @GetMapping("/{id}")
    public ProviderDetailDto getById(
            @PathVariable Long id,
            @RequestParam(required = false) String zip,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng) {
        return providerDetailService.getById(id, zip, location, lat, lng);
    }
}
