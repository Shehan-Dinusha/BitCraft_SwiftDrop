package com.swiftdrop.webhook.service;

import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.security.CallerContext;
import com.swiftdrop.webhook.domain.entity.WebhookDeliveryAttempt;
import com.swiftdrop.webhook.domain.entity.WebhookEndpoint;
import com.swiftdrop.webhook.domain.repository.WebhookDeliveryAttemptRepository;
import com.swiftdrop.webhook.domain.repository.WebhookEndpointRepository;
import com.swiftdrop.webhook.dto.CreateWebhookEndpointRequest;
import com.swiftdrop.webhook.dto.WebhookDeliveryAttemptResponse;
import com.swiftdrop.webhook.dto.WebhookEndpointResponse;
import com.swiftdrop.webhook.exception.WebhookEndpointNotFoundException;
import com.swiftdrop.webhook.service.access.MerchantAccess;
import com.swiftdrop.webhook.service.subscription.EventSubscriptionPatterns;
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

/**
 * Webhook endpoint management. Only {@code MERCHANT} (own endpoints) and {@code SYSTEM_ADMIN} (any merchant)
 * may use it. Another merchant's endpoint is reported as not found, indistinguishable from a missing one.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookSubscriptionService {

    private static final UserRole[] MANAGERS = {UserRole.MERCHANT, UserRole.SYSTEM_ADMIN};

    private final WebhookEndpointRepository endpointRepository;
    private final WebhookDeliveryAttemptRepository attemptRepository;
    private final OutboundWebhookDispatcher dispatcher;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Subscribes a new webhook endpoint, generating a cryptographically secure HMAC secret. A merchant always
     * registers under its own merchant identity; an administrator must name the merchant.
     */
    @Transactional
    public WebhookEndpointResponse createEndpoint(CreateWebhookEndpointRequest request, CallerContext caller) {
        MerchantAccess.requireRole(caller, MANAGERS);
        UUID merchantId = MerchantAccess.ownerForCreate(caller, request.merchantId());
        log.info("Registering webhook endpoint for merchant: {} target: {}", merchantId, request.targetUrl());

        String subscribedEvents = EventSubscriptionPatterns.parseForRegistration(request.subscribedEvents()).canonical();

        byte[] secretBytes = new byte[32];
        secureRandom.nextBytes(secretBytes);
        String generatedSecret = "whsec_" + HexFormat.of().formatHex(secretBytes);

        WebhookEndpoint endpoint = WebhookEndpoint.builder()
                .endpointId(UUID.randomUUID())
                .merchantId(merchantId)
                .targetUrl(request.targetUrl())
                .secretKey(generatedSecret)
                .subscribedEvents(subscribedEvents)
                .active(true)
                .description(request.description())
                .build();

        WebhookEndpoint saved = endpointRepository.save(endpoint);
        log.info("Webhook endpoint {} created successfully for merchant {}", saved.getEndpointId(), saved.getMerchantId());

        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public WebhookEndpointResponse getEndpoint(UUID endpointId, CallerContext caller) {
        return mapToResponse(findAccessibleEndpoint(endpointId, caller));
    }

    /**
     * A merchant lists its own endpoints (asking for another merchant is refused). An administrator may filter by
     * merchant or, without a filter, list every endpoint.
     */
    @Transactional(readOnly = true)
    public List<WebhookEndpointResponse> listEndpoints(UUID requestedMerchantId, CallerContext caller) {
        MerchantAccess.requireRole(caller, MANAGERS);
        UUID scope = MerchantAccess.listScope(caller, requestedMerchantId, UserRole.SYSTEM_ADMIN);
        List<WebhookEndpoint> endpoints = scope != null
                ? endpointRepository.findByMerchantId(scope)
                : endpointRepository.findAll();
        return endpoints.stream().map(this::mapToResponse).toList();
    }

    @Transactional
    public WebhookEndpointResponse toggleEndpointActive(UUID endpointId, boolean active, CallerContext caller) {
        WebhookEndpoint endpoint = findAccessibleEndpoint(endpointId, caller);
        log.info("Toggling endpoint {} active status to {}", endpointId, active);
        endpoint.setActive(active);
        return mapToResponse(endpointRepository.save(endpoint));
    }

    /**
     * Hard-deletes an endpoint. Endpoints referenced by delivery jobs cannot be deleted (foreign key without
     * cascade, so their history survives); the resulting {@code DataIntegrityViolationException} is mapped
     * to 409 and the caller should deactivate the endpoint instead.
     */
    @Transactional
    public void deleteEndpoint(UUID endpointId, CallerContext caller) {
        WebhookEndpoint endpoint = findAccessibleEndpoint(endpointId, caller);
        log.info("Deleting webhook endpoint: {}", endpointId);
        endpointRepository.delete(endpoint);
        // Surface the foreign-key violation here, inside the service, rather than at commit.
        endpointRepository.flush();
    }

    /**
     * Dispatches a test ping event to verify connectivity and HMAC signature computation.
     */
    public WebhookDeliveryAttemptResponse testPing(UUID endpointId, CallerContext caller) {
        WebhookEndpoint endpoint = findAccessibleEndpoint(endpointId, caller);
        log.info("Initiating test ping for endpoint {}", endpointId);

        Map<String, Object> testPayload = Map.of(
                "event", "webhook.test.ping",
                "endpointId", endpoint.getEndpointId().toString(),
                "timestamp", Instant.now().toString(),
                "message", "SwiftDrop Webhook Resiliency ping verification."
        );

        return dispatcher.dispatch(endpoint, "webhook.test.ping", testPayload);
    }

    @Transactional(readOnly = true)
    public List<WebhookDeliveryAttemptResponse> getDeliveryAttempts(UUID endpointId, CallerContext caller) {
        WebhookEndpoint endpoint = findAccessibleEndpoint(endpointId, caller);
        return attemptRepository.findByEndpointIdOrderByAttemptedAtDesc(endpoint.getEndpointId()).stream()
                .map(this::mapAttemptToResponse)
                .toList();
    }

    /**
     * Role check first (403 for roles that may never manage endpoints), then the ownership check. A merchant
     * asking for another merchant's endpoint gets the same exception and message as for an unknown id, so the
     * response does not reveal that the endpoint exists.
     */
    private WebhookEndpoint findAccessibleEndpoint(UUID endpointId, CallerContext caller) {
        MerchantAccess.requireRole(caller, MANAGERS);
        return endpointRepository.findByEndpointId(endpointId)
                .filter(endpoint -> MerchantAccess.canAccess(caller, endpoint.getMerchantId()))
                .orElseThrow(() -> new WebhookEndpointNotFoundException("Webhook endpoint not found with ID: " + endpointId));
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
