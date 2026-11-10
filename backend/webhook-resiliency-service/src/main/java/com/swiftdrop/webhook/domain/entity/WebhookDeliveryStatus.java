package com.swiftdrop.webhook.domain.entity;

/**
 * Lifecycle of a {@link WebhookDelivery}. Mirrors {@code ck_webhook_deliveries_status}.
 * Ingestion only creates {@link #PENDING} jobs; the other transitions belong to the delivery worker.
 */
public enum WebhookDeliveryStatus {
    PENDING,
    IN_FLIGHT,
    SUCCEEDED,
    RETRY,
    /** Terminal failure. This status, not the legacy dead-letter table, is the source of truth for dead letters. */
    DEAD
}
