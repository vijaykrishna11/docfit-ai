package com.docfitai.backend.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.docfitai.backend.account.dto.ShortlistDetailDto;
import com.docfitai.backend.account.dto.ShortlistDto;
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
import org.springframework.web.server.ResponseStatusException;

class ShortlistServiceTest extends PostgresIntegrationSupport {

    @Autowired
    private ShortlistService shortlistService;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createRenameListAndDeleteWork() {
        Long userId = insertUser("shortlist-basic@example.com");

        ShortlistDto created = shortlistService.create(userId, "  Cardiology options  ");
        assertThat(created.name()).isEqualTo("Cardiology options");
        assertThat(created.providerCount()).isZero();

        ShortlistDto renamed = shortlistService.rename(userId, created.id(), "Near campus");
        assertThat(renamed.name()).isEqualTo("Near campus");

        assertThat(shortlistService.list(userId)).extracting(ShortlistDto::name).containsExactly("Near campus");

        shortlistService.delete(userId, created.id());
        assertThat(shortlistService.list(userId)).isEmpty();
    }

    @Test
    void addingAndRemovingAProviderUpdatesTheDetailAndCount() {
        Long userId = insertUser("shortlist-providers@example.com");
        Long providerId = insertProviderWithLocation(
                jdbcTemplate, "8400000001", "Shortlist", "Test", "1 Test St", "Long Beach", "CA", "90802", null, 33.77, -118.19);
        ShortlistDto shortlist = shortlistService.create(userId, "My shortlist");

        shortlistService.addProvider(userId, shortlist.id(), providerId);
        // Adding the same provider twice must be idempotent, not a duplicate row or an error.
        shortlistService.addProvider(userId, shortlist.id(), providerId);

        ShortlistDetailDto detail = shortlistService.getDetail(userId, shortlist.id());
        assertThat(detail.providers()).extracting(dto -> dto.providerId()).containsExactly(providerId);
        assertThat(shortlistService.list(userId).get(0).providerCount()).isEqualTo(1);

        shortlistService.removeProvider(userId, shortlist.id(), providerId);
        assertThat(shortlistService.getDetail(userId, shortlist.id()).providers()).isEmpty();
    }

    @Test
    void addProviderRejectsAnUnknownProviderId() {
        Long userId = insertUser("shortlist-unknown-provider@example.com");
        ShortlistDto shortlist = shortlistService.create(userId, "My shortlist");

        assertThatThrownBy(() -> shortlistService.addProvider(userId, shortlist.id(), 999_999_999L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
                .isEqualTo(404);
    }

    @Test
    void operatingOnAnUnownedOrNonexistentShortlistIs404NotAnException() {
        Long userId = insertUser("shortlist-not-found@example.com");
        assertThatThrownBy(() -> shortlistService.rename(userId, 999_999_999L, "New name"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
                .isEqualTo(404);
    }

    @Test
    void concurrentAddOfTheSameProviderNeverThrowsAndLeavesExactlyOneRow() throws InterruptedException {
        Long userId = insertUser("shortlist-race@example.com");
        Long providerId = insertProviderWithLocation(
                jdbcTemplate, "8400000002", "Race", "Test", "1 Test St", "Long Beach", "CA", "90802", null, 33.77, -118.19);
        ShortlistDto shortlist = shortlistService.create(userId, "Race shortlist");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyToStart = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Runnable task = () -> {
            readyToStart.countDown();
            try {
                start.await(5, TimeUnit.SECONDS);
                shortlistService.addProvider(userId, shortlist.id(), providerId);
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
        assertThat(shortlistService.getDetail(userId, shortlist.id()).providers())
                .extracting(dto -> dto.providerId())
                .containsExactly(providerId);
    }

    private Long insertUser(String email) {
        AppUser user = appUserRepository.save(new AppUser(email, "irrelevant-hash", "Test User", Instant.now(), Instant.now()));
        return user.getId();
    }
}
