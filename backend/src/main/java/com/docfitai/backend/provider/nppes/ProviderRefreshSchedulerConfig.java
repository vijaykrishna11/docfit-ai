package com.docfitai.backend.provider.nppes;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Scheduling infrastructure only spins up at all when explicitly enabled (CLAUDE.md "Scheduler --
 * Default OFF"): {@code docfitai.refresh.scheduler.enabled=true} (env {@code
 * DOCFIT_PROVIDER_REFRESH_ENABLED}). Off by default in every environment, including tests and
 * local dev -- no background thread pool is created unless an operator opts in.
 */
@Configuration
@ConditionalOnProperty(prefix = "docfitai.refresh.scheduler", name = "enabled", havingValue = "true")
@EnableScheduling
public class ProviderRefreshSchedulerConfig {
}
