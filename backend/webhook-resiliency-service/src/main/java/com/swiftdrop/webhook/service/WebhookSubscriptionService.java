package com.swiftdrop.webhook.service;

import com.swiftdrop.webhook.domain.entity.WebhookDeliveryAttempt;
import com.swiftdrop.webhook.domain.entity.WebhookEndpoint;
import com.swiftdrop.webhook.domain.repository.WebhookDeliveryAttemptRepository;
import com.swiftdrop.webhook.domain.repository.WebhookEndpointRepository;
import com.swiftdrop.webhook.dto.CreateWebhookEndpointRequest;
import com.swiftdrop.webhook.dto.WebhookDeliveryAttemptResponse;
import com.swiftdrop.webhook.dto.WebhookEndpointResponse;
import com.swiftdrop.webhook.exception.WebhookEndpointNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookSubscriptionService {

    private final WebhookEndpointRepository endpointRepository;
    private final WebhookDeliveryAttemptRepository attemptRepository;
    private final OutboundWebhookDispatcher dispatcher;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Subscribes a new webhook endpoint for a merchant, generating a cryptographically secure HMAC secret.
     */
    @Transactional
    public WebhookEndpointResponse createEndpoint(CreateWebhookEndpointRequest request) {
        log.info("Registering webhook endpoint for merchant: {} target: {}", request.merchantId(), request.targetUrl());

        byte[] secretBytes = new byte[32];
        secureRandom.nextBytes(secretBytes);
        String generatedSecret = "whsec_" + HexFormat.of().formatHex(secretBytes);

        WebhookEndpoint endpoint = WebhookEndpoint.builder()
                .endpointId(UUID.randomUUID())
                .merchantId(request.merchantId())
                .targetUrl(request.targetUrl())
                .secretKey(generatedSecret)
                .subscribedEvents(request.subscribedEvents() != null ? request.subscribedEvents() : "parcel.status.changed")
                .active(true)
                .description(request.description())
                .build();

        WebhookEndpoint saved = endpointRepository.save(endpoint);
        log.info("Webhook endpoint {} created successfully for merchant {}", saved.getEndpointId(), saved.getMerchantId());

        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public WebhookEndpointResponse getEndpoint(UUID endpointId) {
        return endpointRepository.findByEndpointId(endpointId)
                .map(this::mapToResponse)
                .orElseThrow(() -> new WebhookEndpointNotFoundException("Webhook endpoint not found with ID: " + endpointId));
    }

    @Transactional(readOnly = true)
    public List<WebhookEndpointResponse> listEndpointsByMerchant(UUID merchantId) {
        return endpointRepository.findByMerchantId(merchantId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WebhookEndpointResponse> listAllEndpoints() {
        return endpointRepository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public WebhookEndpointResponse toggleEndpointActive(UUID endpointId, boolean active) {
        log.info("Toggling endpoint {} active status to {}", endpointId, active);
        WebhookEndpoint endpoint = endpointRepository.findByEndpointId(endpointId)
                .orElseThrow(() -> new WebhookEndpointNotFoundException("Webhook endpoint not found with ID: " + endpointId));

        endpoint.setActive(active);
        WebhookEndpoint saved = endpointRepository.save(endpoint);
        return mapToResponse(saved);
    }

    @Transactional
    public void deleteEndpoint(UUID endpointId) {
        log.info("Deleting webhook endpoint: {}", endpointId);
        WebhookEndpoint endpoint = endpointRepository.findByEndpointId(endpointId)
                .orElseThrow(() -> new WebhookEndpointNotFoundException("Webhook endpoint not found with ID: " + endpointId));
        endpointRepository.delete(endpoint);
    }

    /**
     * Dispatches a test ping event to verify connectivity and HMAC signature computation.
     */
    public WebhookDeliveryAttemptResponse testPing(UUID endpointId) {
        log.info("Initiating test ping for endpoint {}", endpointId);
        WebhookEndpoint endpoint = endpointRepository.findByEndpointId(endpointId)
                .orElseThrow(() -> new WebhookEndpointNotFoundException("Webhook endpoint not found with ID: " + endpointId));

        Map<String, Object> testPayload = Map.of(
                "event", "webhook.test.ping",
                "endpointId", endpoint.getEndpointId().toString(),
                "timestamp", Instant.now().toString(),
                "message", "SwiftDrop Webhook Resiliency ping verification."
        );

        return dispatcher.dispatch(endpoint, "webhook.test.ping", testPayload);
    }

    @Transactional(readOnly = true)
    public List<WebhookDeliveryAttemptResponse> getDeliveryAttempts(UUID endpointId) {
        return attemptRepository.findByEndpointIdOrderByAttemptedAtDesc(endpointId).stream()
                .map(this::mapAttemptToResponse)
                .toList();
    }

    private WebhookEndpointResponse mapToResponse(WebhookEndpoint endpoint) {
        return new WebhookEndpointResponse(
                endpoint.getEndpointId(),
                endpoint.getMerchantId(),
                endpoint.getTargetUrl(),
                endpoint.getSecretKey(),
                endpoint.getSubscribedEvents(),
                endpoint.isActive(),
                endpoint.getDescription(),
                endpoint.getCreatedAt()
        );
    }

    private WebhookDeliveryAttemptResponse mapAttemptToResponse(WebhookDeliveryAttempt attempt) {
        return new WebhookDeliveryAttemptResponse(
                attempt.getAttemptId(),
                attempt.getEndpointId(),
                attempt.getMerchantId(),
                attempt.getEventType(),
                attempt.getHttpStatusCode(),
                attempt.getAttemptNumber(),
                attempt.isSuccess(),
                attempt.getResponseBody(),
                attempt.getErrorMessage(),
                attempt.getAttemptedAt()
        );
    }
}
