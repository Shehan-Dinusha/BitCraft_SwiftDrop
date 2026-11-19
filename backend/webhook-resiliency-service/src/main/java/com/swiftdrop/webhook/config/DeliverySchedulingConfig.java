package com.swiftdrop.webhook.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables the delivery worker's schedules ({@code DeliveryWorker}, {@code LeaseRecoveryJob}). Off together with the
 * worker when {@code webhook.delivery.worker-enabled=false}.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "webhook.delivery", name = "worker-enabled", havingValue = "true", matchIfMissing = true)
public class DeliverySchedulingConfig {
}
