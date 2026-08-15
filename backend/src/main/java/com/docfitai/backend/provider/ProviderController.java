package com.docfitai.backend.provider;

import com.docfitai.backend.provider.dto.ProviderDetailDto;
import com.docfitai.backend.provider.dto.ProviderSearchResponseDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/providers")
public class ProviderController {

    private final ProviderSearchService providerSearchService;
    private final ProviderDetailService providerDetailService;

    public ProviderController(ProviderSearchService providerSearchService, ProviderDetailService providerDetailService) {
        this.providerSearchService = providerSearchService;
        this.providerDetailService = providerDetailService;
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
            @RequestParam(defaultValue = "20") int size) {
        return providerSearchService.search(
                new ProviderSearchQuery(specialty, zip, location, lat, lng, radius, sort, page, size));
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
