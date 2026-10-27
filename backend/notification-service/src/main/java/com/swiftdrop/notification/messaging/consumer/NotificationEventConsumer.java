package com.swiftdrop.notification.messaging.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.notification.config.RabbitMQConfig;
import com.swiftdrop.notification.dto.DispatchNotificationRequest;
import com.swiftdrop.notification.service.MultiChannelNotificationDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private final ObjectMapper objectMapper;
    private final MultiChannelNotificationDispatcher dispatcher;

    @RabbitListener(queues = RabbitMQConfig.NOTIFICATION_EVENTS_QUEUE)
    public void onNotificationEvent(Message message) {
        String routingKey = message.getMessageProperties() != null
                ? message.getMessageProperties().getReceivedRoutingKey()
                : "unknown";

        log.info("Received event on notification queue with routing key: {}", routingKey);

        try {
            JsonNode payload = objectMapper.readTree(message.getBody());

            UUID recipientId = null;
            if (payload.hasNonNull("customerId")) {
                try {
                    recipientId = UUID.fromString(payload.get("customerId").asText());
                } catch (IllegalArgumentException ignored) {}
            }
            if (recipientId == null && payload.hasNonNull("recipientId")) {
                try {
                    recipientId = UUID.fromString(payload.get("recipientId").asText());
                } catch (IllegalArgumentException ignored) {}
            }

            if (recipientId == null) {
                log.debug("No customer/recipient ID present in event with key {}. Skipping customer notification.", routingKey);
                return;
            }

            String trackingNumber = payload.hasNonNull("trackingNumber")
                    ? payload.get("trackingNumber").asText()
                    : "SD-PARCEL";

            String status = "CREATED";
            if (payload.hasNonNull("newPublicStatus")) {
                status = payload.get("newPublicStatus").asText();
            } else if (payload.hasNonNull("newStatus")) {
                status = payload.get("newStatus").asText();
            }

            String templateCode = resolveTemplateCode(status);
            String channel = "DELIVERED".equalsIgnoreCase(status) ? "EMAIL" : "SMS";

            Map<String, String> params = new HashMap<>();
            params.put("trackingNumber", trackingNumber);
            params.put("status", status);
            params.put("deliveredAt", "just now");
            if (payload.hasNonNull("failureReason")) {
                params.put("failureReason", payload.get("failureReason").asText());
            }

            DispatchNotificationRequest request = new DispatchNotificationRequest(
                    recipientId,
                    channel,
                    templateCode,
                    null,
                    params,
                    trackingNumber
            );

            dispatcher.dispatch(request);

        } catch (IOException | IllegalArgumentException e) {
            log.error("Failed to parse notification event message: {}", e.getMessage(), e);
        }
    }

    private String resolveTemplateCode(String status) {
        return switch (status.toUpperCase()) {
            case "PICKED_UP" -> "PARCEL_PICKED_UP";
            case "OUT_FOR_DELIVERY" -> "PARCEL_OUT_FOR_DELIVERY";
            case "DELIVERED" -> "PARCEL_DELIVERED";
            case "FAILED" -> "PARCEL_FAILED";
            default -> "PARCEL_CREATED";
        };
    }
}
