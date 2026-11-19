package com.swiftdrop.dispatch.messaging.publisher;

import java.util.Objects;
import java.util.UUID;

/**
 * An outbound RabbitMQ message recorded during a business transaction.
 *
 * <p>It is raised as a Spring application event and only relayed to the broker
 * by {@link AfterCommitRabbitRelay} once the surrounding transaction commits, so a
 * rolled-back assignment never announces itself to downstream services.</p>
 *
 * @param routingKey routing key on {@code swiftdrop.events.exchange}
 * @param payload    the unchanged shared-contract event record
 * @param orderId    order the message concerns, kept for diagnostics
 */
public record OutboundDispatchMessage(String routingKey, Object payload, UUID orderId) {

    public OutboundDispatchMessage {
        Objects.requireNonNull(routingKey, "routingKey");
        Objects.requireNonNull(payload, "payload");
    }
}
