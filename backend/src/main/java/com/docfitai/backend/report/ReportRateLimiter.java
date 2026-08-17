package com.docfitai.backend.report;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * In-memory per-IP sliding-window throttle for directory-correction report submission (CLAUDE.md
 * "Rate Limit Reporting"). Deliberately not shared with {@code AuthRateLimiter} -- login abuse and
 * report-spam abuse warrant different thresholds, and coupling them would make either one harder
 * to tune independently. Same documented limitation as AuthRateLimiter: single-instance only, not
 * distributed; a multi-instance deployment would need a shared store (e.g. Redis) to stay
 * effective, deliberately not added just for this (CLAUDE.md: "Do not add Redis solely for this").
 */
@Component
public class ReportRateLimiter {

    private final int maxAttempts;
    private final Duration window;
    private final ConcurrentMap<String, Deque<Instant>> attemptsByKey = new ConcurrentHashMap<>();

    public ReportRateLimiter(
            @Value("${docfitai.reports.rate-limit.max-attempts:5}") int maxAttempts,
            @Value("${docfitai.reports.rate-limit.window-minutes:10}") long windowMinutes) {
        this.maxAttempts = maxAttempts;
        this.window = Duration.ofMinutes(windowMinutes);
    }

    public void checkAllowed(String key) {
        Instant now = Instant.now();
        Deque<Instant> timestamps = attemptsByKey.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(now.minus(window))) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxAttempts) {
                throw new ResponseStatusException(
                        HttpStatus.TOO_MANY_REQUESTS, "Too many reports submitted. Please try again later.");
            }
            timestamps.addLast(now);
        }
    }
}
