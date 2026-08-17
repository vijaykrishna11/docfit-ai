package com.docfitai.backend.insurance.evidence;

import com.docfitai.backend.insurance.InsuranceNetwork;
import com.docfitai.backend.insurance.InsuranceNetworkRepository;
import com.docfitai.backend.insurance.InsurancePlan;
import com.docfitai.backend.insurance.InsurancePlanRepository;
import com.docfitai.backend.insurance.NetworkSource;
import com.docfitai.backend.insurance.NetworkSourceRepository;
import com.docfitai.backend.insurance.Payer;
import com.docfitai.backend.insurance.PayerRepository;
import com.docfitai.backend.insurance.connector.DemoNetworkConnector;
import com.docfitai.backend.insurance.connector.NetworkParticipationRecord;
import com.docfitai.backend.provider.Provider;
import com.docfitai.backend.provider.ProviderLocationRepository;
import com.docfitai.backend.provider.ProviderRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/**
 * Bounded, one-time-per-provider seeding of clearly-synthetic demo network evidence, so the
 * signature evidence feature is demonstrable without a live payer connector. Runs on startup but
 * only ever INSERTs missing rows -- it never touches an existing evidence row, so the
 * deliberately backdated {@code checked_at} timestamps (spread across fresh/aging/stale bands for
 * demo purposes) survive restarts instead of resetting to "just checked" every time. Bounded to a
 * small number of providers and does zero external network calls (CLAUDE.md 26).
 *
 * <p><b>Production safety (CLAUDE.md 26-27, 62):</b> this must never activate implicitly. It is
 * gated behind {@code docfitai.insurance.synthetic-demo.enabled}, which defaults to {@code false}.
 * An operator (or the test profile) must explicitly opt in; normal `dev`/`prod` startup never
 * seeds synthetic evidence unless that flag is set.
 */
@Component
@ConditionalOnProperty(prefix = "docfitai.insurance.synthetic-demo", name = "enabled", havingValue = "true")
public class DemoNetworkEvidenceSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoNetworkEvidenceSeeder.class);
    private static final int MAX_PROVIDERS = 50;

    private final PayerRepository payerRepository;
    private final NetworkSourceRepository networkSourceRepository;
    private final InsuranceNetworkRepository insuranceNetworkRepository;
    private final InsurancePlanRepository insurancePlanRepository;
    private final ProviderRepository providerRepository;
    private final ProviderLocationRepository providerLocationRepository;
    private final ProviderNetworkEvidenceRepository evidenceRepository;
    private final DemoNetworkConnector demoNetworkConnector;
    private final NetworkEvidenceImportService importService;

    public DemoNetworkEvidenceSeeder(
            PayerRepository payerRepository,
            NetworkSourceRepository networkSourceRepository,
            InsuranceNetworkRepository insuranceNetworkRepository,
            InsurancePlanRepository insurancePlanRepository,
            ProviderRepository providerRepository,
            ProviderLocationRepository providerLocationRepository,
            ProviderNetworkEvidenceRepository evidenceRepository,
            DemoNetworkConnector demoNetworkConnector,
            NetworkEvidenceImportService importService) {
        this.payerRepository = payerRepository;
        this.networkSourceRepository = networkSourceRepository;
        this.insuranceNetworkRepository = insuranceNetworkRepository;
        this.insurancePlanRepository = insurancePlanRepository;
        this.providerRepository = providerRepository;
        this.providerLocationRepository = providerLocationRepository;
        this.evidenceRepository = evidenceRepository;
        this.demoNetworkConnector = demoNetworkConnector;
        this.importService = importService;
    }

    @Override
    public void run(String... args) {
        log.info("Synthetic demo network evidence is ENABLED (docfitai.insurance.synthetic-demo.enabled=true) -- seeding synthetic data now.");
        Payer demoPayer = payerRepository.findByCode(DemoNetworkConnector.SOURCE_CODE).orElse(null);
        if (demoPayer == null) {
            log.debug("Demo network payer not present yet (migrations not applied) -- skipping demo evidence seeding");
            return;
        }
        List<NetworkSource> sources = networkSourceRepository.findByPayerId(demoPayer.getId());
        InsuranceNetwork network = insuranceNetworkRepository
                .findByPayerIdAndExternalNetworkIdentifier(demoPayer.getId(), "DEMO-NETWORK-1")
                .orElse(null);
        InsurancePlan plan = insurancePlanRepository
                .findByPayerIdAndExternalPlanIdentifier(demoPayer.getId(), "DEMO-PLAN-1")
                .orElse(null);
        if (sources.isEmpty() || network == null || plan == null) {
            log.debug("Demo network reference rows incomplete -- skipping demo evidence seeding");
            return;
        }
        NetworkSource source = sources.get(0);

        List<Provider> providers =
                providerRepository.findAll(PageRequest.of(0, MAX_PROVIDERS, Sort.by("id"))).getContent();

        int seeded = 0;
        for (Provider provider : providers) {
            boolean alreadySeeded = evidenceRepository.existsByProviderIdAndNetworkIdAndPlanIdAndSourceId(
                    provider.getId(), network.getId(), plan.getId(), source.getId());
            if (alreadySeeded) {
                continue;
            }
            List<NetworkParticipationRecord> matches = demoNetworkConnector.fetchProviderNetworkParticipation(provider.getNpiNumber());
            var providerLocations = providerLocationRepository.findByProviderIdOrderByPrimaryDescId(provider.getId());
            Instant backdatedCheckedAt =
                    Instant.now().minus(Math.floorMod(provider.getNpiNumber().hashCode(), 90), ChronoUnit.DAYS);
            importService.recordObservation(provider, providerLocations, network, plan, source, matches, backdatedCheckedAt);
            seeded++;
        }
        if (seeded > 0) {
            log.info("Seeded {} synthetic demo network evidence record(s) (bounded to {} providers)", seeded, MAX_PROVIDERS);
        }
    }
}
