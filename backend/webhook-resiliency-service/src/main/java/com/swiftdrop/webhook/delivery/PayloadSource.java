package com.swiftdrop.webhook.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.swiftdrop.webhook.domain.entity.InboundEvent;
import com.swiftdrop.webhook.domain.entity.OrderMerchantMapping;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Everything {@link WebhookPayloadFactory} needs, taken from stored rows only: the inbox event and the order
 * ownership projection. Built by the delivery worker (Step 10) for each attempt; nothing here depends on the attempt.
 *
 * @param eventId        {@code inbound_events.event_id}: becomes the payload {@code id}
 * @param eventType      {@code inbound_events.event_type} (canonical, e.g. {@code parcel.status.changed})
 * @param occurredAt     {@code inbound_events.occurred_at}: the source event's timestamp
 * @param storedPayload  {@code inbound_events.payload}: the internal contract as stored by ingestion
 * @param trackingNumber {@code order_merchant_map.tracking_number}: the only tracking-number source
 */
public record PayloadSource(UUID eventId, String eventType, Instant occurredAt, JsonNode storedPayload,
                            String trackingNumber) {

    public PayloadSource {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(storedPayload, "storedPayload");
    }

    /**
     * Combines an event with the ownership row of its order. Refuses rows that do not belong together (different
     * order, or a merchant other than the one the event was resolved to), so a payload can never carry another
     * order's or another merchant's data.
     */
    public static PayloadSource from(InboundEvent event, OrderMerchantMapping mapping) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(mapping, "mapping");
        if (event.getOrderId() == null || !event.getOrderId().equals(mapping.getOrderId())) {
            throw new IllegalArgumentException("Order mapping does not belong to the event's order");
        }
        if (event.getMerchantId() == null || !event.getMerchantId().equals(mapping.getMerchantId())) {
            throw new IllegalArgumentException("Event is not resolved to the mapping's merchant");
        }
        return new PayloadSource(event.getEventId(), event.getEventType(), event.getOccurredAt(), event.getPayload(),
                mapping.getTrackingNumber());
    }
}
