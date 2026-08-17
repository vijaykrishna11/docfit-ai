package com.docfitai.backend.navigator;

import com.docfitai.backend.insurance.InsurancePlan;
import com.docfitai.backend.insurance.InsurancePlanRepository;
import com.docfitai.backend.navigator.dto.SavedPlanDto;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Explicit opt-in only (CLAUDE.md "Saved Insurance Plan": "Never auto-save insurance selection").
 * Stores a reference to one of DocFit's own public plan records, never member information
 * (CLAUDE.md "Do Not Store Member Information"). One plan per user for MVP -- {@code save} upserts
 * rather than allowing a second row.
 */
@Service
public class SavedPlanService {

    private final UserSavedPlanRepository savedPlanRepository;
    private final InsurancePlanRepository insurancePlanRepository;

    public SavedPlanService(UserSavedPlanRepository savedPlanRepository, InsurancePlanRepository insurancePlanRepository) {
        this.savedPlanRepository = savedPlanRepository;
        this.insurancePlanRepository = insurancePlanRepository;
    }

    public SavedPlanDto get(Long userId) {
        return savedPlanRepository.findByUserId(userId).map(this::toDto).orElse(null);
    }

    @Transactional
    public SavedPlanDto save(Long userId, Long insurancePlanId) {
        InsurancePlan plan = insurancePlanRepository
                .findById(insurancePlanId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown insurance plan."));
        Instant now = Instant.now();
        UserSavedPlan saved = savedPlanRepository
                .findByUserId(userId)
                .map(existing -> {
                    existing.setInsurancePlanId(plan.getId());
                    existing.setUpdatedAt(now);
                    return existing;
                })
                .orElseGet(() -> savedPlanRepository.save(new UserSavedPlan(userId, plan.getId(), now, now)));
        return toDto(savedPlanRepository.save(saved), plan);
    }

    @Transactional
    public void remove(Long userId) {
        savedPlanRepository.findByUserId(userId).ifPresent(savedPlanRepository::delete);
    }

    private SavedPlanDto toDto(UserSavedPlan savedPlan) {
        InsurancePlan plan = insurancePlanRepository.findById(savedPlan.getInsurancePlanId()).orElse(null);
        if (plan == null) {
            return null;
        }
        return toDto(savedPlan, plan);
    }

    private SavedPlanDto toDto(UserSavedPlan savedPlan, InsurancePlan plan) {
        return new SavedPlanDto(
                savedPlan.getId(),
                plan.getPayer().getId(),
                plan.getPayer().getName(),
                plan.getId(),
                plan.getPlanName(),
                savedPlan.getCreatedAt(),
                savedPlan.getUpdatedAt());
    }
}
