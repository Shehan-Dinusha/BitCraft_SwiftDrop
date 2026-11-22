package com.swiftdrop.webhook.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.webhook.domain.entity.AttemptErrorClass;
import com.swiftdrop.webhook.domain.entity.DeadLetterWebhookRecord;
import com.swiftdrop.webhook.domain.entity.WebhookDeliveryAttempt;
import com.swiftdrop.webhook.domain.entity.WebhookEndpoint;
import com.swiftdrop.webhook.domain.repository.DeadLetterWebhookRecordRepository;
import com.swiftdrop.webhook.domain.repository.WebhookDeliveryAttemptRepository;
import com.swiftdrop.webhook.dto.WebhookDeliveryAttemptResponse;
import com.swiftdrop.webhook.resiliency.ExponentialBackoffScheduler;
import com.swiftdrop.webhook.resiliency.HmacSignatureGenerator;
import com.swiftdrop.webhook.security.UnsafeWebhookUrlException;
import com.swiftdrop.webhook.security.WebhookUrlPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

/**
 * Legacy synchronous dispatcher, used only by legacy dead-letter replay ({@link #dispatch}).
 *
 * <p>Every attempt is recorded with the real HTTP status (null when no response arrived), a classification
 * and the latency. The remote response body is discarded unread, and neither logs nor stored rows contain the
 * target URL or raw exception text.</p>
 *
 * <p>Before every attempt the stored target URL is re-checked with {@link WebhookUrlPolicy}; a refused destination
 * is recorded as {@link AttemptErrorClass#BLOCKED_DESTINATION} without any request being made.</p>
 *
 * @deprecated Event ingestion no longer calls this class; it creates persistent {@code webhook_deliveries}
 * jobs instead. Failed deliveries are
 * replayed by {@link FailedDeliveryService} and test pings use {@link TestPingService}. Do not add new callers.
 */
@Deprecated
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboundWebhookDispatcher {

    private static final Duration ATTEMPT_TIMEOUT = Duration.ofSeconds(3);

    private final WebClient webClient;
    private final HmacSignatureGenerator hmacSignatureGenerator;
    private final ExponentialBackoffScheduler backoffScheduler;
    private final WebhookDeliveryAttemptRepository attemptRepository;
    private final DeadLetterWebhookRecordRepository dlqRepository;
    private final ObjectMapper objectMapper;
    private final WebhookUrlPolicy urlPolicy;

    /**
     * Legacy replay: up to {@link ExponentialBackoffScheduler#MAX_ATTEMPTS} attempts, then a legacy dead-letter
     * row with a sanitised failure reason.
     */
    public WebhookDeliveryAttemptResponse dispatch(WebhookEndpoint endpoint, String eventType, Object payloadObj) {
        String payloadJson = toJson(payloadObj);
        WebhookDeliveryAttempt lastAttempt = null;

        for (int attemptNumber = 1; attemptNumber <= ExponentialBackoffScheduler.MAX_ATTEMPTS; attemptNumber++) {
            lastAttempt = sendOnce(endpoint, eventType, payloadJson, attemptNumber);
            if (lastAttempt.isSuccess()) {
                return AttemptResponseMapper.toResponse(lastAttempt);
            }
            if (lastAttempt.getErrorClass() == AttemptErrorClass.BLOCKED_DESTINATION) {
                // A policy refusal is permanent and nothing was sent: no retry and no new dead-letter record.
                return AttemptResponseMapper.toResponse(lastAttempt);
            }
            if (backoffScheduler.canRetry(attemptNumber)) {
                // Synchronous legacy path: a short pause only. Real scheduled backoff belongs to the delivery worker.
                try {
                    Thread.sleep(Math.min(backoffScheduler.getBackoffDelayMs(attemptNumber + 1), 500L));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        log.error("All attempts failed for webhook endpoint {} (event {}, last errorClass {}). Writing legacy dead-letter record.",
                endpoint.getEndpointId(), eventType, lastAttempt.getErrorClass());
        dlqRepository.save(DeadLetterWebhookRecord.builder()
                .dlqId(UUID.randomUUID())
                .endpointId(endpoint.getEndpointId())
                .merchantId(endpoint.getMerchantId())
                .eventType(eventType)
                .payload(payloadJson)
                .failureReason(DlqFailureClassifier.storedReason(lastAttempt.getErrorClass(), lastAttempt.getHttpStatusCode()))
                .totalAttempts(lastAttempt.getAttemptNumber())
                .replayed(false)
                .build());

        return AttemptResponseMapper.toResponse(lastAttempt);
    }

    /** One signed POST. Never throws for HTTP or network failures; the outcome is recorded and returned. */
    private WebhookDeliveryAttempt sendOnce(WebhookEndpoint endpoint, String eventType, String payloadJson, int attemptNumber) {
        UUID attemptId = UUID.randomUUID();
        long started = System.nanoTime();

        // Re-checked on every attempt: stored URLs may predate the policy. Nothing is sent when it is refused.
        Optional<UnsafeWebhookUrlException.Reason> refused = urlPolicy.check(endpoint.getTargetUrl());
        if (refused.isPresent()) {
            log.warn("Webhook attempt {} for endpoint {} (event {}) refused before sending: errorClass={} reason={}",
                    attemptNumber, endpoint.getEndpointId(), eventType, AttemptErrorClass.BLOCKED_DESTINATION,
                    refused.get());
            return recordAttempt(endpoint, eventType, payloadJson, attemptNumber, attemptId, null,
                    AttemptErrorClass.BLOCKED_DESTINATION, elapsedMs(started));
        }

        String signature = hmacSignatureGenerator.calculateSignature(payloadJson, endpoint.getSecretKey());
        Integer statusCode = null;
        AttemptErrorClass errorClass;
        try {
            statusCode = webClient.post()
                    .uri(endpoint.getTargetUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-SwiftDrop-Signature", signature)
                    .header("X-SwiftDrop-Event", eventType)
                    .header("X-SwiftDrop-Delivery-Id", attemptId.toString())
                    .header("X-SwiftDrop-Timestamp", String.valueOf(System.currentTimeMillis()))
                    .bodyValue(payloadJson)
                    // exchangeToMono: any status is a normal result, and the body is released without being read.
                    .exchangeToMono(response -> response.releaseBody().thenReturn(response.statusCode().value()))
                    .timeout(ATTEMPT_TIMEOUT)
                    .block();
            errorClass = statusCode == null ? AttemptErrorClass.INVALID_RESPONSE : classifyStatus(statusCode);
        } catch (RuntimeException failure) {
            // WebClient/Reactor failures (connect, TLS, timeout) are unchecked; classifyException maps
            // each type to a retryable or permanent attempt result.
            errorClass = classifyException(failure);
        }
        int latencyMs = elapsedMs(started);
        WebhookDeliveryAttempt attempt = recordAttempt(endpoint, eventType, payloadJson, attemptNumber, attemptId,
                statusCode, errorClass, latencyMs);
        if (attempt.isSuccess()) {
            log.info("Webhook attempt {} for endpoint {} (event {}) succeeded: status={} latencyMs={}",
                    attemptNumber, endpoint.getEndpointId(), eventType, statusCode, latencyMs);
        } else {
            log.warn("Webhook attempt {} for endpoint {} (event {}) failed: errorClass={} status={} latencyMs={}",
                    attemptNumber, endpoint.getEndpointId(), eventType, errorClass, statusCode, latencyMs);
        }
        return attempt;
    }

    private WebhookDeliveryAttempt recordAttempt(WebhookEndpoint endpoint, String eventType, String payloadJson,
                                                 int attemptNumber, UUID attemptId, Integer statusCode,
                                                 AttemptErrorClass errorClass, int latencyMs) {
        boolean success = errorClass == null;
        return attemptRepository.save(WebhookDeliveryAttempt.builder()
                .attemptId(attemptId)
                .endpointId(endpoint.getEndpointId())
                .merchantId(endpoint.getMerchantId())
                .eventType(eventType)
                .payload(payloadJson)
                .httpStatusCode(statusCode)
                .attemptNumber(attemptNumber)
                .success(success)
                .errorClass(errorClass)
                .errorMessage(success ? null : DlqFailureClassifier.describe(errorClass, statusCode).summary())
                .latencyMs(latencyMs)
                .build());
    }

    private static int elapsedMs(long startedNanos) {
        return (int) Math.min(Integer.MAX_VALUE, Duration.ofNanos(System.nanoTime() - startedNanos).toMillis());
    }

    /** Null means success (2xx). */
    private static AttemptErrorClass classifyStatus(int status) {
        if (status >= 200 && status < 300) return null;
        if (status == 429) return AttemptErrorClass.HTTP_RATE_LIMITED;
        if (status >= 400 && status < 500) return AttemptErrorClass.HTTP_CLIENT_ERROR;
        if (status >= 500 && status < 600) return AttemptErrorClass.HTTP_SERVER_ERROR;
        return AttemptErrorClass.INVALID_RESPONSE;
    }

    /** Uses exception types only; exception messages (which contain URLs and hosts) are never read. */
    private static AttemptErrorClass classifyException(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof TimeoutException || cause instanceof SocketTimeoutException
                    || cause instanceof io.netty.handler.timeout.TimeoutException) {
                return AttemptErrorClass.TIMEOUT;
            }
            if (cause instanceof ConnectException || cause instanceof UnknownHostException
                    || cause instanceof NoRouteToHostException || cause instanceof SSLException) {
                return AttemptErrorClass.CONNECTION_FAILURE;
            }
            if (cause.getCause() == cause) break;
        }
        return failure instanceof WebClientRequestException ? AttemptErrorClass.CONNECTION_FAILURE : AttemptErrorClass.UNKNOWN;
    }

    private String toJson(Object payloadObj) {
        if (payloadObj instanceof String str) {
            return str;
        }
        try {
            return objectMapper.writeValueAsString(payloadObj);
        } catch (JsonProcessingException serialisationFailure) {
            return "{}";
        }
    }
}
