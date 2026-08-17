package com.docfitai.backend.insurance.connector;

import com.docfitai.backend.provider.Provider;
import com.docfitai.backend.provider.ProviderLocation;
import com.docfitai.backend.provider.ProviderLocationRepository;
import com.docfitai.backend.provider.ProviderRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The only connector active by default (and only when explicitly enabled -- see
 * DemoNetworkEvidenceSeeder). Produces small, deterministic, clearly-synthetic participation
 * records for DocFit's own "DocFit Demo Network (synthetic test data)" payer -- never a real
 * payer. See CLAUDE.md 42 and docs/insurance-network-architecture.md ("Demo data").
 *
 * <p>Looking up the provider's own practice location to sometimes echo it back is a deliberate
 * simulation of what a real source's matched-location field would contain -- it is not a
 * shortcut a real connector could take (a real payer source reports its own address data, not
 * DocFit's).
 */
@Component
public class DemoNetworkConnector implements ProviderNetworkConnector {

    public static final String SOURCE_CODE = "DOCFIT_DEMO";
    private static final String NETWORK_ID = "DEMO-NETWORK-1";
    private static final String PLAN_ID = "DEMO-PLAN-1";

    private final ProviderRepository providerRepository;
    private final ProviderLocationRepository providerLocationRepository;

    public DemoNetworkConnector(ProviderRepository providerRepository, ProviderLocationRepository providerLocationRepository) {
        this.providerRepository = providerRepository;
        this.providerLocationRepository = providerLocationRepository;
    }

    @Override
    public String sourceCode() {
        return SOURCE_CODE;
    }

    @Override
    public List<DiscoveredPlan> discoverPlans() {
        return List.of(new DiscoveredPlan(PLAN_ID, "DocFit Demo PPO (synthetic)", "PPO", NETWORK_ID, "DocFit Demo Network Directory (synthetic)"));
    }

    @Override
    public List<NetworkParticipationRecord> fetchProviderNetworkParticipation(String npi) {
        int bucket = Math.floorMod(npi.hashCode(), 10);
        Instant sourceUpdated = Instant.now().minus(Math.floorMod(npi.hashCode(), 45), ChronoUnit.DAYS);

        if (bucket <= 2) {
            // Simulated exact location confirmation, against the provider's primary practice location.
            ProviderLocation location = primaryLocation(npi);
            if (location != null) {
                return List.of(new NetworkParticipationRecord(
                        npi, NETWORK_ID, PLAN_ID, location.getAddressLine1(), location.getCity(), location.getStateCode(),
                        location.getPostalCode(), sourceUpdated));
            }
            return List.of(new NetworkParticipationRecord(npi, NETWORK_ID, PLAN_ID, null, null, null, null, sourceUpdated));
        }
        if (bucket <= 4) {
            // Source confirms the NPI but reports no location -- NPI-only match.
            return List.of(new NetworkParticipationRecord(npi, NETWORK_ID, PLAN_ID, null, null, null, null, sourceUpdated));
        }
        if (bucket == 5) {
            // Source reports only a postal code that happens to match -- simulated by reusing the
            // provider's real postal code with a different street line.
            ProviderLocation location = primaryLocation(npi);
            String postal = location != null ? location.getPostalCode() : "90802";
            return List.of(new NetworkParticipationRecord(
                    npi, NETWORK_ID, PLAN_ID, "Different suite reported by source", "Unspecified", "CA", postal, sourceUpdated));
        }
        if (bucket == 9) {
            // Two conflicting records for the same network -- ambiguous match.
            return List.of(
                    new NetworkParticipationRecord(npi, NETWORK_ID, PLAN_ID, "123 Example St", "Long Beach", "CA", "90802", sourceUpdated),
                    new NetworkParticipationRecord(npi, NETWORK_ID, PLAN_ID, "456 Other Ave", "Long Beach", "CA", "90803", sourceUpdated));
        }
        // buckets 6-8: checked, no evidence found.
        return List.of();
    }

    private ProviderLocation primaryLocation(String npi) {
        Provider provider = providerRepository.findByNpiNumber(npi).orElse(null);
        if (provider == null) {
            return null;
        }
        List<ProviderLocation> locations = providerLocationRepository.findByProviderIdOrderByPrimaryDescId(provider.getId());
        return locations.isEmpty() ? null : locations.get(0);
    }

    @Override
    public ConnectorHealth healthCheck() {
        return new ConnectorHealth(true, "Synthetic demo connector -- always healthy, no external network call.");
    }
}
