package com.swiftdrop.contracts.events.webhook;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Published when an outbound webhook should be dispatched to a merchant endpoint.
 * Routing key: {@code webhook.dispatch.requested}
 */
public record WebhookDispatchRequestedEvent(

        @JsonProperty("eventId")
        UUID eventId,

        @JsonProperty("correlationId")
        UUID correlationId,

        @JsonProperty("webhookId")
        UUID webhookId,

        @JsonProperty("merchantId")
        UUID merchantId,

        @JsonProperty("targetUrl")
        String targetUrl,

        @JsonProperty("httpMethod")
        String httpMethod,

        @JsonProperty("eventType")
        String eventType,

        @JsonProperty("payload")
        Map<String, Object> payload,

        @JsonProperty("headers")
        Map<String, String> headers,

        @JsonProperty("maxRetries")
        int maxRetries,

        @JsonProperty("timestamp")
        Instant timestamp

) {
    public WebhookDispatchRequestedEvent {
        if (eventId == null) eventId = UUID.randomUUID();
        if (correlationId == null) correlationId = UUID.randomUUID();
        if (timestamp == null) timestamp = Instant.now();
        if (httpMethod == null) httpMethod = "POST";
        if (maxRetries <= 0) maxRetries = 3;
    }
}
