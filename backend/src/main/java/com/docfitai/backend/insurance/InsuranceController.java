package com.docfitai.backend.insurance;

import com.docfitai.backend.insurance.dto.InsurancePlanDto;
import com.docfitai.backend.insurance.dto.PayerDto;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Real payer/plan reference data, distinct from the legacy, purely informational
 * {@code /api/insurance-carriers} endpoint. A payer with no integrated plans is still listed
 * here (DocFit AI knows about it) -- {@code hasIntegratedPlans} is what tells the frontend
 * whether to show a plan selector or the "verification not currently available" message.
 */
@RestController
@RequestMapping("/api/insurance")
public class InsuranceController {

    private final PayerRepository payerRepository;
    private final InsurancePlanRepository insurancePlanRepository;

    public InsuranceController(PayerRepository payerRepository, InsurancePlanRepository insurancePlanRepository) {
        this.payerRepository = payerRepository;
        this.insurancePlanRepository = insurancePlanRepository;
    }

    @GetMapping("/payers")
    public List<PayerDto> listPayers() {
        return payerRepository.findAllByOrderByNameAsc().stream()
                .map(payer -> new PayerDto(
                        payer.getId(), payer.getCode(), payer.getName(), insurancePlanRepository.existsByPayerId(payer.getId())))
                .toList();
    }

    @GetMapping("/payers/{id}/plans")
    public List<InsurancePlanDto> listPlans(@PathVariable Long id) {
        payerRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown payer"));
        return insurancePlanRepository.findByPayerIdAndActiveTrue(id).stream()
                .map(plan -> new InsurancePlanDto(plan.getId(), plan.getPayer().getId(), plan.getPlanName(), plan.getPlanType().name()))
                .toList();
    }
}
