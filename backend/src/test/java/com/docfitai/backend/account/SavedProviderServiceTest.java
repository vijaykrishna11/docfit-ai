package com.docfitai.backend.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.docfitai.backend.account.dto.SavedProviderDto;
import com.docfitai.backend.testsupport.PostgresIntegrationSupport;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class SavedProviderServiceTest extends PostgresIntegrationSupport {

    @Autowired
    private SavedProviderService savedProviderService;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void saveIsIdempotentAndRemoveIsScopedToTheOwner() {
        Long userId = insertUser("saved-provider-basic@example.com");
        Long providerId = insertProviderWithLocation(
                jdbcTemplate, "9200000001", "Basic", "Doctor", "1 Test St", "Test City", "CA", "90802", null, 33.77, -118.19);

        savedProviderService.save(userId, providerId);
        savedProviderService.save(userId, providerId);

        List<SavedProviderDto> saved = savedProviderService.list(userId);
        assertThat(saved).extracting(SavedProviderDto::providerId).containsExactly(providerId);

        savedProviderService.remove(userId, providerId);
        assertThat(savedProviderService.list(userId)).isEmpty();
    }

    @Test
    void concurrentSaveOfTheSameProviderNeverThrowsAndLeavesExactlyOneRow() throws InterruptedException {
        Long userId = insertUser("saved-provider-race@example.com");
        Long providerId = insertProviderWithLocation(
                jdbcTemplate, "9200000002", "Race", "Doctor", "1 Test St", "Test City", "CA", "90802", null, 33.77, -118.19);

        // Simulates a double-click: two requests for the same provider both pass the "not saved
        // yet" check before either commits, then race to insert -- the DB's
        // UNIQUE (user_id, provider_id) constraint lets only one succeed, and
        // SavedProviderService.save must absorb that as a no-op rather than letting a
        // DataIntegrityViolationException escape to the caller.
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyToStart = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Runnable task = () -> {
            readyToStart.countDown();
            try {
                start.await(5, TimeUnit.SECONDS);
                savedProviderService.save(userId, providerId);
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            }
        };
        try {
            executor.submit(task);
            executor.submit(task);
            readyToStart.await(5, TimeUnit.SECONDS);
            start.countDown();
        } finally {
            executor.shutdown();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(failure.get()).isNull();
        assertThat(savedProviderService.list(userId)).extracting(SavedProviderDto::providerId).containsExactly(providerId);
    }

    private Long insertUser(String email) {
        AppUser user = appUserRepository.save(new AppUser(email, "irrelevant-hash", "Test User", Instant.now(), Instant.now()));
        return user.getId();
    }
}
