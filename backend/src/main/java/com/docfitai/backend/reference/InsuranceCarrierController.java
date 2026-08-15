package com.docfitai.backend.reference;

import com.docfitai.backend.reference.dto.InsuranceCarrierDto;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/insurance-carriers")
public class InsuranceCarrierController {

    private final InsuranceCarrierRepository insuranceCarrierRepository;

    public InsuranceCarrierController(InsuranceCarrierRepository insuranceCarrierRepository) {
        this.insuranceCarrierRepository = insuranceCarrierRepository;
    }

    @GetMapping
    public List<InsuranceCarrierDto> list() {
        return insuranceCarrierRepository.findAll().stream()
                .map(c -> new InsuranceCarrierDto(c.getId(), c.getName()))
                .toList();
    }
}
