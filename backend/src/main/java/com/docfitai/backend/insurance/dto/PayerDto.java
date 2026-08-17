package com.docfitai.backend.insurance.dto;

public record PayerDto(Long id, String code, String name, boolean hasIntegratedPlans) {
}
