package com.docfitai.backend.reference;

import com.docfitai.backend.reference.dto.LocationSuggestionDto;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/locations")
public class LocationSuggestionController {

    private final LocationSuggestionService locationSuggestionService;

    public LocationSuggestionController(LocationSuggestionService locationSuggestionService) {
        this.locationSuggestionService = locationSuggestionService;
    }

    @GetMapping("/suggestions")
    public List<LocationSuggestionDto> suggestions(@RequestParam(defaultValue = "") String q) {
        return locationSuggestionService.suggest(q);
    }
}
