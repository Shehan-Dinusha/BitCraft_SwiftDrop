package com.swiftdrop.webhook.delivery;

import java.util.Objects;
import java.util.UUID;

/**
 * One outbound attempt, as the delivery worker (Step 10) will build it.
 *
 * @param targetUrl  the endpoint's stored target URL (re-validated by the sender)
 * @param secret     the endpoint's stored signing secret, exactly as stored (including {@code whsec_})
 * @param eventId    inbound event id: {@link WebhookHeaders#EVENT_ID}, identical for every attempt and replay
 * @param deliveryId delivery job id: {@link WebhookHeaders#DELIVERY_ID}, the same for every retry of the job
 * @param eventType  canonical event type: {@link WebhookHeaders#EVENT}
 * @param attempt    1-based attempt number of the job: {@link WebhookHeaders#ATTEMPT}
 * @param body       the payload bytes from {@link WebhookPayloadFactory#buildBytes}; copied on construction so the
 *                   bytes that are signed and the bytes that are sent are the same array, which no caller can change
 */
public record WebhookRequest(String targetUrl, String secret, UUID eventId, UUID deliveryId, String eventType,
                             int attempt, byte[] body) {

    public WebhookRequest {
        Objects.requireNonNull(targetUrl, "targetUrl");
        Objects.requireNonNull(secret, "secret");
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(deliveryId, "deliveryId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(body, "body");
        if (attempt < 1) {
            throw new IllegalArgumentException("attempt must be at least 1");
        }
        body = body.clone();
    }

    /** A copy: the internal array is the exact signed-and-sent body and is never handed out. */
    @Override
    public byte[] body() {
        return body.clone();
    }

    /** Never prints the secret, URL or body. */
    @Override
    public String toString() {
        return "WebhookRequest[eventId=" + eventId + ", deliveryId=" + deliveryId + ", eventType=" + eventType
                + ", attempt=" + attempt + ", bodyBytes=" + body.length + "]";
    }

    /** The internal body array, for the sender only (same package): signed and sent without any copy in between. */
    byte[] bodyForSending() {
        return body;
    }
}
