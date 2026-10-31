package com.swiftdrop.webhook.messaging.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.webhook.config.RabbitMQConfig;
import com.swiftdrop.webhook.domain.entity.WebhookEndpoint;
import com.swiftdrop.webhook.domain.repository.WebhookEndpointRepository;
import com.swiftdrop.webhook.service.OutboundWebhookDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryStateWebhookConsumer {

    private final ObjectMapper objectMapper;
    private final WebhookEndpointRepository endpointRepository;
    private final OutboundWebhookDispatcher dispatcher;

    @RabbitListener(queues = RabbitMQConfig.WEBHOOK_EVENTS_QUEUE)
    public void onWebhookEvent(Message message) {
        String routingKey = message.getMessageProperties() != null
                ? message.getMessageProperties().getReceivedRoutingKey()
                : "unknown";

        log.info("Received message on webhook queue with routing key: {}", routingKey);

        try {
            JsonNode jsonNode = objectMapper.readTree(message.getBody());
            String eventType = determineEventType(routingKey, jsonNode);

            UUID merchantId = null;
            if (jsonNode.hasNonNull("merchantId")) {
                try {
                    merchantId = UUID.fromString(jsonNode.get("merchantId").asText());
                } catch (IllegalArgumentException ignored) {
                }
            }

            // Find matching active endpoints
            List<WebhookEndpoint> candidateEndpoints = merchantId != null
                    ? endpointRepository.findByMerchantIdAndActiveTrue(merchantId)
                    : endpointRepository.findByActiveTrue();

            for (WebhookEndpoint endpoint : candidateEndpoints) {
                if (isSubscribed(endpoint.getSubscribedEvents(), eventType)) {
                    log.info("Dispatching event {} to endpoint {} ({})", eventType, endpoint.getEndpointId(), endpoint.getTargetUrl());
                    dispatcher.dispatch(endpoint, eventType, jsonNode);
                }
            }

        } catch (IOException | IllegalArgumentException e) {
            log.error("Failed to process incoming webhook message with routing key {}: {}", routingKey, e.getMessage(), e);
        }
    }

    private String determineEventType(String routingKey, JsonNode jsonNode) {
        if (jsonNode.hasNonNull("eventType")) {
            return jsonNode.get("eventType").asText();
        }
        if (routingKey != null && !routingKey.isBlank() && !routingKey.equals("unknown")) {
            return routingKey;
        }
        if (jsonNode.hasNonNull("trackingNumber") || jsonNode.hasNonNull("newPublicStatus")) {
            return "parcel.status.changed";
        }
        return "system.generic.event";
    }

    private boolean isSubscribed(String subscribedEvents, String eventType) {
        if (subscribedEvents == null || subscribedEvents.isBlank() || subscribedEvents.equals("*")) {
            return true;
        }
        String[] tokens = subscribedEvents.split(",");
        for (String token : tokens) {
            String trimmed = token.trim();
            if (trimmed.equalsIgnoreCase(eventType) || trimmed.equals("*") || eventType.startsWith(trimmed.replace(".#", ""))) {
                return true;
            }
        }
        return false;
    }
}
