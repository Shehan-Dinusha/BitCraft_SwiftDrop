package com.swiftdrop.notification.messaging.trigger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Encapsulates the AMQP message metadata and JSON payload for incoming domain events.
 * Provides convenience helper methods to extract standard fields (recipient, tracking number, status).
 */
@Slf4j
public record EventEnvelope(
        String routingKey,
        UUID eventId,
        UUID correlationId,
        JsonNode payload
) {

    public static Optional<EventEnvelope> from(Message message, ObjectMapper objectMapper) {
        if (message == null || message.getBody() == null) {
            return Optional.empty();
        }

        String routingKey = message.getMessageProperties() != null
                ? message.getMessageProperties().getReceivedRoutingKey()
                : "unknown";

        try {
            JsonNode payload = objectMapper.readTree(message.getBody());
            if (payload == null || payload.isNull()) {
                return Optional.empty();
            }

            UUID eventId = parseUuid(payload, "eventId").orElse(null);
            UUID correlationId = parseUuid(payload, "correlationId").orElse(null);

            return Optional.of(new EventEnvelope(routingKey, eventId, correlationId, payload));
        } catch (IOException e) {
            log.error("Failed to parse event message with routing key {}: {}", routingKey, e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<UUID> extractRecipientId() {
        Optional<UUID> customerId = parseUuid(payload, "customerId");
        if (customerId.isPresent()) {
            return customerId;
        }
        return parseUuid(payload, "recipientId");
    }

    /**
     * The merchant company of a merchant order. customer-order-service stores the merchant id in
     * customerId for those orders, so an event is a merchant order when merchantId is set and customerId
     * is the same id (or absent). A customer order that merely names a merchant is not one.
     */
    public Optional<UUID> merchantOrderMerchantId() {
        Optional<UUID> merchantId = parseUuid(payload, "merchantId");
        if (merchantId.isEmpty()) {
            return Optional.empty();
        }
        Optional<UUID> customerId = parseUuid(payload, "customerId");
        return customerId.isEmpty() || customerId.get().equals(merchantId.get()) ? merchantId : Optional.empty();
    }

    public String extractTrackingNumber() {
        if (payload.hasNonNull("trackingNumber") && !payload.get("trackingNumber").asText().isBlank()) {
            return payload.get("trackingNumber").asText().trim();
        }
        return null; // no placeholder: a made-up number in a message is worse than none
    }

    public Optional<String> extractStatus() {
        if (payload.hasNonNull("newPublicStatus")) {
            return Optional.of(payload.get("newPublicStatus").asText());
        }
        if (payload.hasNonNull("newStatus")) {
            return Optional.of(payload.get("newStatus").asText());
        }
        if (payload.hasNonNull("status")) {
            return Optional.of(payload.get("status").asText());
        }
        return Optional.empty();
    }

    private static Optional<UUID> parseUuid(JsonNode node, String fieldName) {
        if (node.hasNonNull(fieldName)) {
            try {
                return Optional.of(UUID.fromString(node.get(fieldName).asText()));
            } catch (IllegalArgumentException ignored) {
                // Return empty if not a valid UUID format
            }
        }
        return Optional.empty();
    }
}
