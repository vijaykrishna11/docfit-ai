package com.docfitai.backend.insurance.evidence;

import com.docfitai.backend.insurance.InsurancePlan;
import com.docfitai.backend.insurance.InsurancePlanRepository;
import com.docfitai.backend.insurance.dto.NetworkEvidenceDetailDto;
import com.docfitai.backend.provider.ProviderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/providers")
public class NetworkEvidenceController {

    private final ProviderRepository providerRepository;
    private final InsurancePlanRepository insurancePlanRepository;
    private final NetworkEvidenceService networkEvidenceService;

    public NetworkEvidenceController(
            ProviderRepository providerRepository,
            InsurancePlanRepository insurancePlanRepository,
            NetworkEvidenceService networkEvidenceService) {
        this.providerRepository = providerRepository;
        this.insurancePlanRepository = insurancePlanRepository;
        this.networkEvidenceService = networkEvidenceService;
    }

    @GetMapping("/{id}/network-evidence")
    public NetworkEvidenceDetailDto getNetworkEvidence(@PathVariable Long id, @RequestParam Long planId) {
        if (!providerRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown provider");
        }
        InsurancePlan plan = insurancePlanRepository
                .findById(planId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown plan"));
        return networkEvidenceService.lookup(id, plan);
    }
}
