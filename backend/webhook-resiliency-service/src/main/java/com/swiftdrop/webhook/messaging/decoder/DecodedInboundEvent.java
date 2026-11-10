package com.swiftdrop.webhook.messaging.decoder;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.swiftdrop.webhook.service.subscription.WebhookEventType;

import java.time.Instant;
import java.util.UUID;

/**
 * A validated inbound event.
 *
 * @param sourceService      AMQP app-id when the producer set one, otherwise {@code null}
 * @param claimedMerchantId  the merchant named by {@code order.created}; always {@code null} for other types
 * @param trackingNumber     producer's tracking number, informational only (not a resolution key)
 * @param payload            contract fields only, customer identifiers removed; safe to persist
 */
public record DecodedInboundEvent(
        UUID eventId,
        WebhookEventType type,
        String schemaVersion,
        String sourceService,
        UUID correlationId,
        UUID orderId,
        UUID claimedMerchantId,
        String trackingNumber,
        Instant occurredAt,
        ObjectNode payload
) {
}
