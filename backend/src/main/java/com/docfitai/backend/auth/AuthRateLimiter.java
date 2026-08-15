package com.docfitai.backend.auth;

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
 * Lightweight in-memory per-key (IP + normalized email) sliding-window throttle for
 * login/register abuse. Deliberately simple for a single-instance demo deployment --
 * NOT distributed, resets on restart, and would need a shared store (e.g. Redis) behind a
 * load balancer with multiple instances. That is documented as deployment-layer future work
 * rather than pulling in Redis just for this.
 */
@Component
public class AuthRateLimiter {

    private final int maxAttempts;
    private final Duration window;
    private final ConcurrentMap<String, Deque<Instant>> attemptsByKey = new ConcurrentHashMap<>();

    public AuthRateLimiter(
            @Value("${docfitai.auth.rate-limit.max-attempts:10}") int maxAttempts,
            @Value("${docfitai.auth.rate-limit.window-minutes:5}") long windowMinutes) {
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
                        HttpStatus.TOO_MANY_REQUESTS, "Too many attempts. Please try again later.");
            }
            timestamps.addLast(now);
        }
    }
}
