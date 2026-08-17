package com.docfitai.backend.provider.nppes;

import static org.assertj.core.api.Assertions.assertThat;

import com.docfitai.backend.testsupport.PostgresIntegrationSupport;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** CLAUDE.md "Overlap Protection": a Postgres advisory lock, not an in-JVM flag (safe across multiple instances). */
class ProviderRefreshLockTest extends PostgresIntegrationSupport {

    @Autowired
    private ProviderRefreshLock lock;

    @Test
    void aSecondCallIsSkippedWhileTheFirstIsStillHoldingTheLock() {
        List<Boolean> innerTaskRan = new ArrayList<>();

        boolean outerRan = lock.runIfNotAlreadyRunning(() -> {
            // A separate JDBC connection attempting the same lock while the outer task is still
            // running (still holding its connection open) must be told "already running," not
            // block or run concurrently.
            boolean innerAcquired = lock.runIfNotAlreadyRunning(() -> innerTaskRan.add(true));
            assertThat(innerAcquired).isFalse();
        });

        assertThat(outerRan).isTrue();
        assertThat(innerTaskRan).isEmpty();
    }

    @Test
    void theLockIsReleasedAfterwardsSoASubsequentCallSucceeds() {
        boolean first = lock.runIfNotAlreadyRunning(() -> {});
        boolean second = lock.runIfNotAlreadyRunning(() -> {});

        assertThat(first).isTrue();
        assertThat(second).isTrue();
    }
}
