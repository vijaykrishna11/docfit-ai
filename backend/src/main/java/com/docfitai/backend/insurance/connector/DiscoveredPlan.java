package com.docfitai.backend.insurance.connector;

public record DiscoveredPlan(
        String externalPlanId, String planName, String planType, String externalNetworkId, String networkName) {
}
