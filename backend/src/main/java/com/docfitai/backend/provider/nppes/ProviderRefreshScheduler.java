package com.docfitai.backend.provider.nppes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Optional, operator-configured scheduled refresh (CLAUDE.md "Scheduler -- Default OFF"). Same
 * kill switch as {@link ProviderRefreshSchedulerConfig} -- this bean only exists at all when
 * {@code docfitai.refresh.scheduler.enabled=true}. Overlap-protected via {@link ProviderRefreshLock}
 * (a Postgres advisory lock): if a previous run is still in flight when the next scheduled firing
 * happens, this run is skipped entirely rather than running concurrently or queueing up.
 */
@Component
@ConditionalOnProperty(prefix = "docfitai.refresh.scheduler", name = "enabled", havingValue = "true")
public class ProviderRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(ProviderRefreshScheduler.class);

    private final ProviderRefreshLock lock;
    private final ProviderRefreshService refreshService;
    private final NppesRefreshProperties refreshProperties;

    public ProviderRefreshScheduler(
            ProviderRefreshLock lock, ProviderRefreshService refreshService, NppesRefreshProperties refreshProperties) {
        this.lock = lock;
        this.refreshService = refreshService;
        this.refreshProperties = refreshProperties;
    }

    @Scheduled(cron = "${docfitai.refresh.scheduler.cron:0 0 3 * * *}")
    public void scheduledRefresh() {
        if (refreshProperties.getNpis().isEmpty()) {
            log.info("Scheduled provider refresh skipped -- no NPIs configured (docfitai.refresh.nppes.npis).");
            return;
        }
        boolean ran;
        try {
            ran = lock.runIfNotAlreadyRunning(() -> refreshService.refreshByNpis(refreshProperties.getNpis()));
        } catch (Exception e) {
            // A scheduled task's exception must never propagate into the scheduler's thread pool
            // in a way that could destabilize it or, worse, the rest of the application -- the
            // underlying ProviderRefreshService already marks its own DataImport row FAILED before
            // rethrowing, so this is strictly a "log and move on" boundary, not a place doing any
            // real error handling of its own.
            log.error("Scheduled provider refresh failed: {}", e.getMessage(), e);
            return;
        }
        if (!ran) {
            log.info("Scheduled provider refresh skipped -- another refresh is already running.");
        }
    }
}
