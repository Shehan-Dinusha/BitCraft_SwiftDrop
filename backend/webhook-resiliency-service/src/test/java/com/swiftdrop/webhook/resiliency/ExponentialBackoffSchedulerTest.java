package com.swiftdrop.webhook.resiliency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExponentialBackoffSchedulerTest {

    private ExponentialBackoffScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ExponentialBackoffScheduler();
    }

    @Test
    @DisplayName("getBackoffDelayMs should compute exponential delay: attempt 1 -> 0ms, attempt 2 -> 2000ms, attempt 3 -> 4000ms")
    void testGetBackoffDelayMs() {
        assertThat(scheduler.getBackoffDelayMs(1)).isEqualTo(0L);
        assertThat(scheduler.getBackoffDelayMs(2)).isEqualTo(2000L);
        assertThat(scheduler.getBackoffDelayMs(3)).isEqualTo(4000L);
    }

    @Test
    @DisplayName("canRetry should return true for attempts < 3 and false for attempts >= 3")
    void testCanRetry() {
        assertThat(scheduler.canRetry(1)).isTrue();
        assertThat(scheduler.canRetry(2)).isTrue();
        assertThat(scheduler.canRetry(3)).isFalse();
        assertThat(scheduler.canRetry(4)).isFalse();
    }
}
