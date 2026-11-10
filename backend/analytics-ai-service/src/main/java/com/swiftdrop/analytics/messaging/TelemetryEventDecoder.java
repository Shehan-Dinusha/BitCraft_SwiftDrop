package com.swiftdrop.analytics.messaging;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.swiftdrop.contracts.events.EventRoutingKeys;
import com.swiftdrop.contracts.events.delivery.ParcelStatusChangedEvent;
import com.swiftdrop.contracts.events.dispatch.CourierAssignedEvent;
import com.swiftdrop.contracts.events.order.OrderCreatedEvent;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
public class TelemetryEventDecoder {
    private final ObjectMapper objectMapper;

    public TelemetryEventDecoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
    }

    public Optional<TelemetryEvent> decode(Message message) {
        String key = message.getMessageProperties().getReceivedRoutingKey();
        // Never choose a class from __TypeId__ or guess from payload fields.
        Class<?> type = switch (key == null ? "" : key) {
            case EventRoutingKeys.PARCEL_STATUS_CHANGED -> ParcelStatusChangedEvent.class;
            case EventRoutingKeys.DISPATCH_COURIER_ASSIGNED -> CourierAssignedEvent.class;
            case EventRoutingKeys.ORDER_CREATED -> OrderCreatedEvent.class;
            default -> null;
        };
        if (type == null) return Optional.empty();

        try {
            JsonNode tree = objectMapper.readTree(message.getBody());
            if (tree == null || !tree.isObject()) throw new IllegalArgumentException();
            // Check before shared compact constructors can generate defaults.
            UUID eventId = uuid(tree, "eventId");
            UUID orderId = uuid(tree, "orderId");
            String correlationField = type == ParcelStatusChangedEvent.class && !tree.hasNonNull("correlationId")
                    ? "traceId" : "correlationId";
            UUID correlationId = uuid(tree, correlationField);
            Instant occurredAt = Instant.parse(text(tree, "timestamp"));

            if (type == ParcelStatusChangedEvent.class) {
                // Do not let alias field ordering replace the verified trace ID.
                if (tree.hasNonNull("correlationId") && tree.hasNonNull("traceId")
                        && !correlationId.equals(uuid(tree, "traceId"))) throw new IllegalArgumentException();
                ((ObjectNode) tree).put("correlationId", correlationId.toString());
                ((ObjectNode) tree).remove("traceId");
            }

            Object payload = objectMapper.treeToValue(tree, type);
            switch (payload) {
                case ParcelStatusChangedEvent event -> {
                    text(tree, "trackingNumber");
                    if (event.newPublicStatus() == null || event.district() == null) throw new IllegalArgumentException();
                }
                case CourierAssignedEvent event -> {
                    uuid(tree, "courierId");
                    String name = text(tree, "courierName");
                    if (name.length() > 255) throw new IllegalArgumentException();
                    Instant.parse(text(tree, "assignedAt"));
                    if (event.estimatedPickupMinutes() < 0) throw new IllegalArgumentException();
                }
                case OrderCreatedEvent event -> {
                    text(tree, "trackingNumber");
                    if (event.pickupDistrict() == null || event.deliveryDistrict() == null) throw new IllegalArgumentException();
                }
                default -> throw new IllegalArgumentException();
            }
            return Optional.of(new TelemetryEvent(key, eventId, orderId, correlationId, occurredAt, payload));
        } catch (Exception invalid) {
            // No raw JSON, customer identifiers or parser excerpts in the exception.
            throw new AmqpRejectAndDontRequeueException("Invalid supported analytics event: " + key);
        }
    }

    private static String text(JsonNode tree, String name) {
        JsonNode value = tree.get(name);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) throw new IllegalArgumentException();
        return value.textValue();
    }

    private static UUID uuid(JsonNode tree, String name) {
        String value = text(tree, name);
        UUID id = UUID.fromString(value);
        if (!id.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException();
        return id;
    }
}
