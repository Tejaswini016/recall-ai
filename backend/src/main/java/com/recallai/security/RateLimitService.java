package com.recallai.security;

import com.recallai.ai.AiProperties;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

/**
 * Per-user fixed-window rate limiter for expensive endpoints. Kept in memory: the
 * application runs as a single instance and the limit exists to bound AI spend, not to
 * enforce a hard quota. Entries expire with their window and are swept lazily, so the map
 * cannot grow without bound.
 */
@Service
public class RateLimitService {

    static final Duration WINDOW = Duration.ofHours(1);
    private static final int SWEEP_EVERY_CALLS = 1000;

    private final AiProperties properties;
    private final Clock clock;
    private final Map<Long, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong calls = new AtomicLong();

    public RateLimitService(AiProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * @param allowed           whether the request may proceed
     * @param limit             requests allowed per window
     * @param remaining         requests left in the current window after this one
     * @param retryAfterSeconds seconds until the window resets (meaningful when denied)
     */
    public record Decision(boolean allowed, int limit, int remaining, long retryAfterSeconds) {
    }

    private record Window(long startMillis, int count) {
    }

    /** Consumes one request for the user if the window has capacity. */
    public Decision tryConsume(Long userId) {
        long now = clock.millis();
        int limit = properties.rateLimitPerHour();
        sweepIfDue(now);

        Window updated = windows.compute(userId, (id, current) -> {
            if (current == null || now - current.startMillis() >= WINDOW.toMillis()) {
                return new Window(now, 1);
            }
            return new Window(current.startMillis(), current.count() + 1);
        });

        long resetInSeconds = Math.max(1, (updated.startMillis() + WINDOW.toMillis() - now + 999) / 1000);
        if (updated.count() > limit) {
            return new Decision(false, limit, 0, resetInSeconds);
        }
        return new Decision(true, limit, limit - updated.count(), resetInSeconds);
    }

    private void sweepIfDue(long now) {
        if (calls.incrementAndGet() % SWEEP_EVERY_CALLS == 0) {
            windows.entrySet().removeIf(entry -> now - entry.getValue().startMillis() >= WINDOW.toMillis());
        }
    }

    /** Drops every window. Used by tests that reset the database (user ids start over). */
    public void clear() {
        windows.clear();
    }

    int trackedUsers() {
        return windows.size();
    }
}
