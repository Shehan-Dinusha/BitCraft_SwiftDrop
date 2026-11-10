package com.swiftdrop.webhook.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * In-process retry of a failed ingestion transaction (for example a database blip) before the message is
 * rejected to the dead-letter queue. Bounded on purpose: the consumer thread is blocked while it waits.
 *
 * @param maxAttempts total attempts including the first
 */
@ConfigurationProperties(prefix = "webhook.ingestion.retry")
public record IngestionRetryProperties(
        @DefaultValue("3") int maxAttempts,
        @DefaultValue("1s") Duration initialInterval,
        @DefaultValue("2.0") double multiplier,
        @DefaultValue("10s") Duration maxInterval
) {
    public IngestionRetryProperties {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("webhook.ingestion.retry.max-attempts must be at least 1");
        }
    }
}
