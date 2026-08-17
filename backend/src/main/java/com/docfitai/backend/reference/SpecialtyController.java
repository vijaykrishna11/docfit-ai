package com.docfitai.backend.reference;

import com.docfitai.backend.reference.dto.SpecialtyDto;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/specialties")
public class SpecialtyController {

    private final SpecialtyRepository specialtyRepository;

    public SpecialtyController(SpecialtyRepository specialtyRepository) {
        this.specialtyRepository = specialtyRepository;
    }

    @GetMapping
    public List<SpecialtyDto> list() {
        return specialtyRepository.findAll().stream()
                .map(s -> new SpecialtyDto(s.getCode(), s.getName(), s.getDescription()))
                .toList();
    }
}
