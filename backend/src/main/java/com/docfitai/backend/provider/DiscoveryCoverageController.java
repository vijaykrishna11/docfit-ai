package com.docfitai.backend.provider;

import com.docfitai.backend.provider.dto.CoverageDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public, unauthenticated: aggregate counts only, nothing sensitive (CLAUDE.md "Coverage API"). */
@RestController
@RequestMapping("/api/discovery")
public class DiscoveryCoverageController {

    private final DiscoveryCoverageService coverageService;

    public DiscoveryCoverageController(DiscoveryCoverageService coverageService) {
        this.coverageService = coverageService;
    }

    @GetMapping("/coverage")
    public CoverageDto getCoverage() {
        return coverageService.getCoverage();
    }
}
