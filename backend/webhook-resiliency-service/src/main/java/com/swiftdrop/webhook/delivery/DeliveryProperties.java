package com.swiftdrop.webhook.delivery;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/**
 * Outbound delivery settings ({@code webhook.delivery.*}). Defaults are the approved decisions; startup fails on
 * invalid values.
 *
 * @param retryDelays        D1: delay before attempt 2, 3, 4 (default 30 s, 2 min, 10 min). Attempt 1 is immediate,
 *                           so the maximum number of attempts is {@code retryDelays.size() + 1} (default 4).
 * @param jitterRatio        D1: each scheduled delay is multiplied by a uniform random factor in
 *                           {@code [1 - jitterRatio, 1 + jitterRatio]} (default 0.2 = ±20 %)
 * @param retryAfterCap      D2: largest delay a {@code Retry-After} header may request (default 10 min)
 * @param maxConcurrency     D9 (used from Step 10): concurrent requests per replica (default 4)
 * @param maxPerEndpoint     D9 (used from Step 10): concurrent requests per endpoint per replica (default 1)
 * @param claimBatchSize     D9 (used from Step 10): jobs claimed per poll (default 10)
 * @param pollInterval       D9 (used from Step 10): poll interval (default 2 s)
 * @param leaseDuration      D9 (used from Step 10): lease held on a claimed job (default 60 s)
 */
@ConfigurationProperties(prefix = "webhook.delivery")
public record DeliveryProperties(
        @DefaultValue({"30s", "2m", "10m"}) List<Duration> retryDelays,
        @DefaultValue("0.2") double jitterRatio,
        @DefaultValue("10m") Duration retryAfterCap,
        @DefaultValue("4") int maxConcurrency,
        @DefaultValue("1") int maxPerEndpoint,
        @DefaultValue("10") int claimBatchSize,
        @DefaultValue("2s") Duration pollInterval,
        @DefaultValue("60s") Duration leaseDuration
) {
    public DeliveryProperties {
        if (retryDelays == null || retryDelays.isEmpty()) {
            throw new IllegalArgumentException("webhook.delivery.retry-delays must list at least one delay");
        }
        retryDelays = List.copyOf(retryDelays);
        for (Duration delay : retryDelays) {
            requirePositive(delay, "retry-delays");
        }
        if (!(jitterRatio >= 0.0 && jitterRatio < 1.0)) {
            throw new IllegalArgumentException("webhook.delivery.jitter-ratio must be in [0, 1)");
        }
        requirePositive(retryAfterCap, "retry-after-cap");
        requirePositive(pollInterval, "poll-interval");
        requirePositive(leaseDuration, "lease-duration");
        if (maxConcurrency < 1 || maxPerEndpoint < 1 || claimBatchSize < 1 || maxPerEndpoint > maxConcurrency) {
            throw new IllegalArgumentException("webhook.delivery concurrency settings must be positive, "
                    + "with max-per-endpoint <= max-concurrency");
        }
    }

    /** Attempt 1 plus one attempt per configured retry delay (default 4). */
    public int maxAttempts() {
        return retryDelays.size() + 1;
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("webhook.delivery." + name + " must be positive");
        }
    }
}
