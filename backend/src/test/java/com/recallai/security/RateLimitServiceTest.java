package com.recallai.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.recallai.ai.AiProperties;
import com.recallai.ai.AiProvider;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class RateLimitServiceTest {

    private static final Instant START = Instant.parse("2026-09-09T10:00:00Z");

    private static AiProperties limit(int perHour) {
        return new AiProperties("key", "model", "medium", 4096, 12000, 30, 15, 1, 1, perHour, false, AiProvider.ANTHROPIC, "", "gemini-3.6-flash", "", "openai/gpt-oss-120b", "https://api.groq.com/openai/v1");
    }

    /** A clock the test can advance. */
    private static final class MutableClock extends Clock {
        private Instant now = START;

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }
    }

    @Test
    void allowsUpToTheLimitThenDenies() {
        RateLimitService service = new RateLimitService(limit(3), new MutableClock());

        assertThat(service.tryConsume(1L).allowed()).isTrue();
        assertThat(service.tryConsume(1L).remaining()).isEqualTo(1);
        RateLimitService.Decision last = service.tryConsume(1L);
        assertThat(last.allowed()).isTrue();
        assertThat(last.remaining()).isZero();

        RateLimitService.Decision denied = service.tryConsume(1L);
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.limit()).isEqualTo(3);
        assertThat(denied.remaining()).isZero();
        assertThat(denied.retryAfterSeconds()).isEqualTo(3600);
    }

    @Test
    void usersAreLimitedIndependently() {
        RateLimitService service = new RateLimitService(limit(1), new MutableClock());

        assertThat(service.tryConsume(1L).allowed()).isTrue();
        assertThat(service.tryConsume(1L).allowed()).isFalse();
        assertThat(service.tryConsume(2L).allowed()).isTrue();
    }

    @Test
    void windowResetsAfterAnHourAndRetryAfterCountsDown() {
        MutableClock clock = new MutableClock();
        RateLimitService service = new RateLimitService(limit(1), clock);
        service.tryConsume(1L);

        clock.advance(Duration.ofMinutes(20));
        RateLimitService.Decision denied = service.tryConsume(1L);
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.retryAfterSeconds()).isEqualTo(40 * 60);

        clock.advance(Duration.ofMinutes(40));
        assertThat(service.tryConsume(1L).allowed()).isTrue();
    }

    @Test
    void expiredWindowsAreSweptSoMemoryStaysBounded() {
        MutableClock clock = new MutableClock();
        RateLimitService service = new RateLimitService(limit(5), clock);
        for (long user = 1; user <= 500; user++) {
            service.tryConsume(user);
        }
        assertThat(service.trackedUsers()).isEqualTo(500);

        clock.advance(Duration.ofHours(2));
        for (int i = 0; i < 1000; i++) {
            service.tryConsume(9999L);
        }
        assertThat(service.trackedUsers()).isEqualTo(1);
    }
}
