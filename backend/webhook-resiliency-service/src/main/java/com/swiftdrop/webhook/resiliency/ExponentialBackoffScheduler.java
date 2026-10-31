package com.swiftdrop.webhook.resiliency;

import org.springframework.stereotype.Component;

@Component
public class ExponentialBackoffScheduler {

    private static final long BASE_DELAY_MS = 2000L;
    private static final long MAX_DELAY_MS = 30000L;
    public static final int MAX_ATTEMPTS = 3;

    /**
     * Calculates exponential backoff delay in milliseconds.
     * Attempt 1 -> 0ms (immediate)
     * Attempt 2 -> 2000ms
     * Attempt 3 -> 4000ms
     */
    public long getBackoffDelayMs(int attemptNumber) {
        if (attemptNumber <= 1) {
            return 0L;
        }
        long delay = (long) (BASE_DELAY_MS * Math.pow(2, attemptNumber - 2));
        return Math.min(delay, MAX_DELAY_MS);
    }

    public boolean canRetry(int currentAttempt) {
        return currentAttempt < MAX_ATTEMPTS;
    }
}
