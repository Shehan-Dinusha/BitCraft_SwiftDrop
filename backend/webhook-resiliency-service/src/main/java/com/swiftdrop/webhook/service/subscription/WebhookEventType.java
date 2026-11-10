package com.swiftdrop.webhook.service.subscription;

import com.swiftdrop.contracts.events.EventRoutingKeys;
import com.swiftdrop.contracts.events.delivery.ParcelStatusChangedEvent;
import com.swiftdrop.contracts.events.order.OrderCreatedEvent;

import java.util.Arrays;
import java.util.Optional;

/**
 * Catalogue of events that can be ingested and subscribed to. The webhook event type is the
 * shared routing key, so merchants see the same names as the internal contracts.
 *
 * <p>{@code webhook.dispatch.requested} is bound to the queue (broker definitions) but deliberately
 * absent: no service publishes it and it lets the publisher choose the target URL.</p>
 */
public enum WebhookEventType {

    ORDER_CREATED(EventRoutingKeys.ORDER_CREATED, OrderCreatedEvent.class),
    PARCEL_STATUS_CHANGED(EventRoutingKeys.PARCEL_STATUS_CHANGED, ParcelStatusChangedEvent.class);

    private final String eventType;
    private final Class<? extends Record> contract;

    WebhookEventType(String eventType, Class<? extends Record> contract) {
        this.eventType = eventType;
        this.contract = contract;
    }

    public String eventType() {
        return eventType;
    }

    public Class<? extends Record> contract() {
        return contract;
    }

    /**
     * Resolves a received routing key. The queue binds {@code parcel.status.changed.#}, so more specific
     * parcel keys are accepted and normalised to the base event type.
     */
    public static Optional<WebhookEventType> fromRoutingKey(String routingKey) {
        if (routingKey == null) {
            return Optional.empty();
        }
        if (routingKey.startsWith(PARCEL_STATUS_CHANGED.eventType + ".")) {
            return Optional.of(PARCEL_STATUS_CHANGED);
        }
        return fromEventType(routingKey);
    }

    public static Optional<WebhookEventType> fromEventType(String eventType) {
        return Arrays.stream(values()).filter(type -> type.eventType.equals(eventType)).findFirst();
    }
}
