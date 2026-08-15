package com.docfitai.backend.provider;

import com.docfitai.backend.provider.dto.ProviderSearchResponseDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/providers")
public class ProviderController {

    private final ProviderSearchService providerSearchService;

    public ProviderController(ProviderSearchService providerSearchService) {
        this.providerSearchService = providerSearchService;
    }

    @GetMapping("/search")
    public ProviderSearchResponseDto search(
            @RequestParam String specialty,
            @RequestParam String zip,
            @RequestParam(defaultValue = "25") double radius,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return providerSearchService.search(specialty, zip, radius, page, size);
    }
}
