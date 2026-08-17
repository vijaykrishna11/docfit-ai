package com.docfitai.backend.insurance.connector.fhir;

import static org.assertj.core.api.Assertions.assertThat;

import com.docfitai.backend.insurance.connector.DiscoveredPlan;
import com.docfitai.backend.insurance.connector.NetworkParticipationRecord;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * Contract test against saved, spec-shaped Da Vinci Plan-Net fixtures -- never a live payer
 * endpoint (CLAUDE.md 82-83).
 */
class FhirPlanNetParserTest {

    private final FhirPlanNetParser parser = new FhirPlanNetParser(new ObjectMapper());

    @Test
    void parsesPractitionerRoleBundleIntoParticipationRecordWithMatchedLocation() {
        String json = readFixture("plan-net-practitioner-role-bundle.json");

        List<NetworkParticipationRecord> records = parser.parsePractitionerRoleBundle(json, "1234567890");

        assertThat(records).hasSize(1);
        NetworkParticipationRecord record = records.get(0);
        assertThat(record.npi()).isEqualTo("1234567890");
        assertThat(record.externalNetworkId()).isEqualTo("network-1");
        assertThat(record.addressLine1()).isEqualTo("200 Ocean Blvd");
        assertThat(record.city()).isEqualTo("Long Beach");
        assertThat(record.stateCode()).isEqualTo("CA");
        assertThat(record.postalCode()).isEqualTo("90802");
        assertThat(record.sourceLastUpdatedAt()).isNotNull();
    }

    @Test
    void parsesInsurancePlanBundleIntoDiscoveredPlan() {
        String json = readFixture("plan-net-insurance-plan-bundle.json");

        List<DiscoveredPlan> plans = parser.parseInsurancePlanBundle(json);

        assertThat(plans).hasSize(1);
        DiscoveredPlan plan = plans.get(0);
        assertThat(plan.externalPlanId()).isEqualTo("plan-1");
        assertThat(plan.planName()).isEqualTo("Example PPO");
        assertThat(plan.planType()).isEqualTo("PPO");
        assertThat(plan.externalNetworkId()).isEqualTo("network-1");
    }

    @Test
    void malformedJsonFailsClearlyRatherThanGuessing() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> parser.parsePractitionerRoleBundle("not json", "123"))
                .isInstanceOf(FhirPlanNetParser.FhirParseException.class);
    }

    private static String readFixture(String name) {
        try {
            Path path = Path.of("src/test/resources/fhir/" + name);
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
