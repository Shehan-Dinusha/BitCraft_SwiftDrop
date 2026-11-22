package com.swiftdrop.webhook.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.webhook.delivery.AttemptOutcome;
import com.swiftdrop.webhook.delivery.WebhookHttpSender;
import com.swiftdrop.webhook.delivery.WebhookRequest;
import com.swiftdrop.webhook.domain.entity.WebhookDeliveryAttempt;
import com.swiftdrop.webhook.domain.entity.WebhookEndpoint;
import com.swiftdrop.webhook.domain.repository.WebhookDeliveryAttemptRepository;
import com.swiftdrop.webhook.dto.WebhookDeliveryAttemptResponse;
import com.swiftdrop.webhook.dto.outbound.WebhookPayloadV1;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sends one test message through the same sender as real events, so the headers, the signature and the envelope a
 * merchant sees in a test are the ones they will get in production. No retry and no delivery job.
 */
@Service
@RequiredArgsConstructor
public class TestPingService {

    static final String EVENT_TYPE = "webhook.test.ping";

    private final WebhookHttpSender sender;
    private final WebhookDeliveryAttemptRepository attempts;
    private final ObjectMapper objectMapper;

    public WebhookDeliveryAttemptResponse ping(WebhookEndpoint endpoint) {
        UUID eventId = UUID.randomUUID();
        byte[] body = body(eventId);
        Instant attemptedAt = Instant.now();
        AttemptOutcome outcome = sender.send(new WebhookRequest(endpoint.getTargetUrl(), endpoint.getSecretKey(),
                eventId, UUID.randomUUID(), EVENT_TYPE, 1, body));
        WebhookDeliveryAttempt attempt = attempts.save(WebhookDeliveryAttempt.builder()
                .attemptId(UUID.randomUUID())
                .endpointId(endpoint.getEndpointId())
                .merchantId(endpoint.getMerchantId())
                .eventType(EVENT_TYPE)
                .payload(new String(body, java.nio.charset.StandardCharsets.UTF_8))
                .httpStatusCode(outcome.httpStatus())
                .attemptNumber(1)
                .success(outcome.success())
                .errorClass(outcome.errorClass())
                .errorMessage(outcome.success() ? null
                        : DlqFailureClassifier.describe(outcome.errorClass(), outcome.httpStatus()).summary())
                .latencyMs(outcome.latencyMs())
                .attemptedAt(attemptedAt)
                .build());
        return AttemptResponseMapper.toResponse(attempt);
    }

    /** The v1 envelope of a real event, with a message instead of parcel data. */
    private byte[] body(UUID eventId) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("id", eventId.toString());
        envelope.put("type", EVENT_TYPE);
        envelope.put("apiVersion", WebhookPayloadV1.API_VERSION);
        envelope.put("occurredAt", Instant.now().truncatedTo(ChronoUnit.MICROS).toString());
        envelope.put("data", Map.of("message", "Test message from SwiftDrop"));
        try {
            return objectMapper.writeValueAsBytes(envelope);
        } catch (JsonProcessingException impossible) {
            throw new IllegalStateException("Test message could not be written", impossible);
        }
    }
}
