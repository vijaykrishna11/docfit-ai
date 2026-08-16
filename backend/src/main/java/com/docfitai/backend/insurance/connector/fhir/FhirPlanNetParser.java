package com.docfitai.backend.insurance.connector.fhir;

import com.docfitai.backend.insurance.connector.DiscoveredPlan;
import com.docfitai.backend.insurance.connector.NetworkParticipationRecord;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Parses the Da Vinci PDex Plan-Net FHIR R4 resource shapes DocFit AI actually needs
 * (PractitionerRole/Location for network participation, InsurancePlan for plan discovery) --
 * see docs/insurance-network-research.md. Deliberately narrow: no general-purpose FHIR
 * server/client behavior, no HAPI FHIR dependency (CLAUDE.md 21-22). Any malformed/unexpected
 * structure fails clearly (a caught, logged parse error) rather than silently guessing
 * (CLAUDE.md 84) -- it never corrupts existing evidence, since the caller only writes evidence
 * for entries that parsed successfully.
 */
public final class FhirPlanNetParser {

    private static final String NETWORK_EXTENSION_URL =
            "http://hl7.org/fhir/us/davinci-pdex-plan-net/StructureDefinition/plannet-Network";

    private final ObjectMapper objectMapper;

    public FhirPlanNetParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<DiscoveredPlan> parseInsurancePlanBundle(String json) {
        JsonNode bundle = readTree(json);
        List<DiscoveredPlan> plans = new ArrayList<>();
        for (JsonNode entry : entries(bundle)) {
            JsonNode resource = entry.path("resource");
            if (!"InsurancePlan".equals(resource.path("resourceType").asText(null))) {
                continue;
            }
            String planId = resource.path("id").asText(null);
            String planName = resource.path("name").asText(null);
            if (planId == null || planName == null) {
                continue;
            }
            String planType = resource.path("type").path(0).path("coding").path(0).path("code").asText(null);
            JsonNode networkRef = resource.path("network").path(0).path("reference");
            String networkId = networkRef.isMissingNode() ? planId : stripReferencePrefix(networkRef.asText(planId));
            plans.add(new DiscoveredPlan(planId, planName, planType == null ? "OTHER" : planType, networkId, planName));
        }
        return plans;
    }

    public List<NetworkParticipationRecord> parsePractitionerRoleBundle(String json, String npi) {
        JsonNode bundle = readTree(json);
        Map<String, JsonNode> locationsById = new HashMap<>();
        List<JsonNode> practitionerRoles = new ArrayList<>();

        for (JsonNode entry : entries(bundle)) {
            JsonNode resource = entry.path("resource");
            String resourceType = resource.path("resourceType").asText(null);
            if ("Location".equals(resourceType)) {
                locationsById.put(resource.path("id").asText(""), resource);
            } else if ("PractitionerRole".equals(resourceType)) {
                practitionerRoles.add(resource);
            }
        }

        List<NetworkParticipationRecord> records = new ArrayList<>();
        for (JsonNode role : practitionerRoles) {
            String networkId = findNetworkReference(role);
            if (networkId == null) {
                continue;
            }
            JsonNode locationRef = role.path("location").path(0).path("reference");
            JsonNode location = locationRef.isMissingNode() ? null : locationsById.get(stripReferencePrefix(locationRef.asText("")));
            String addressLine1 = null;
            String city = null;
            String stateCode = null;
            String postalCode = null;
            if (location != null) {
                JsonNode address = location.path("address");
                addressLine1 = address.path("line").path(0).asText(null);
                city = address.path("city").asText(null);
                stateCode = address.path("state").asText(null);
                postalCode = address.path("postalCode").asText(null);
            }
            Instant lastUpdated = parseInstant(role.path("meta").path("lastUpdated").asText(null));
            records.add(new NetworkParticipationRecord(npi, networkId, null, addressLine1, city, stateCode, postalCode, lastUpdated));
        }
        return records;
    }

    private static String findNetworkReference(JsonNode practitionerRole) {
        for (JsonNode extension : practitionerRole.path("extension")) {
            if (NETWORK_EXTENSION_URL.equals(extension.path("url").asText(null))) {
                String reference = extension.path("valueReference").path("reference").asText(null);
                if (reference != null) {
                    return stripReferencePrefix(reference);
                }
            }
        }
        return null;
    }

    private static String stripReferencePrefix(String reference) {
        int slash = reference.lastIndexOf('/');
        return slash >= 0 ? reference.substring(slash + 1) : reference;
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            return null;
        }
    }

    private static Iterable<JsonNode> entries(JsonNode bundle) {
        List<JsonNode> result = new ArrayList<>();
        bundle.path("entry").forEach(result::add);
        return result;
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new FhirParseException("Malformed FHIR Bundle JSON from Plan-Net source", e);
        }
    }

    public static class FhirParseException extends RuntimeException {
        public FhirParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
