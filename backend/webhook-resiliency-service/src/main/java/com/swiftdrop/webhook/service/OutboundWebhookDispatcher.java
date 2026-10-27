package com.swiftdrop.webhook.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.webhook.domain.entity.DeadLetterWebhookRecord;
import com.swiftdrop.webhook.domain.entity.WebhookDeliveryAttempt;
import com.swiftdrop.webhook.domain.entity.WebhookEndpoint;
import com.swiftdrop.webhook.domain.repository.DeadLetterWebhookRecordRepository;
import com.swiftdrop.webhook.domain.repository.WebhookDeliveryAttemptRepository;
import com.swiftdrop.webhook.dto.WebhookDeliveryAttemptResponse;
import com.swiftdrop.webhook.resiliency.ExponentialBackoffScheduler;
import com.swiftdrop.webhook.resiliency.HmacSignatureGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboundWebhookDispatcher {

    private final WebClient webClient;
    private final HmacSignatureGenerator hmacSignatureGenerator;
    private final ExponentialBackoffScheduler backoffScheduler;
    private final WebhookDeliveryAttemptRepository attemptRepository;
    private final DeadLetterWebhookRecordRepository dlqRepository;
    private final ObjectMapper objectMapper;

    /**
     * Dispatches a webhook payload with HMAC-SHA256 signing and exponential retries.
     */
    public WebhookDeliveryAttemptResponse dispatch(WebhookEndpoint endpoint, String eventType, Object payloadObj) {
        String payloadJson;
        if (payloadObj instanceof String str) {
            payloadJson = str;
        } else {
            try {
                payloadJson = objectMapper.writeValueAsString(payloadObj);
            } catch (Exception e) {
                payloadJson = "{}";
            }
        }

        int attemptNumber = 1;
        WebhookDeliveryAttempt lastAttempt = null;
        boolean delivered = false;

        while (attemptNumber <= ExponentialBackoffScheduler.MAX_ATTEMPTS && !delivered) {
            UUID attemptId = UUID.randomUUID();
            long timestampMs = System.currentTimeMillis();
            String signature = hmacSignatureGenerator.calculateSignature(payloadJson, endpoint.getSecretKey());

            log.info("Executing webhook dispatch attempt {}/{} to URL: {} for event: {}",
                    attemptNumber, ExponentialBackoffScheduler.MAX_ATTEMPTS, endpoint.getTargetUrl(), eventType);

            try {
                var responseSpec = webClient.post()
                        .uri(endpoint.getTargetUrl())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-SwiftDrop-Signature", signature)
                        .header("X-SwiftDrop-Event", eventType)
                        .header("X-SwiftDrop-Delivery-Id", attemptId.toString())
                        .header("X-SwiftDrop-Timestamp", String.valueOf(timestampMs))
                        .bodyValue(payloadJson)
                        .retrieve()
                        .toEntity(String.class)
                        .timeout(Duration.ofSeconds(3))
                        .block();

                int statusCode = responseSpec != null ? responseSpec.getStatusCode().value() : 200;
                String body = responseSpec != null && responseSpec.getBody() != null
                        ? responseSpec.getBody().substring(0, Math.min(250, responseSpec.getBody().length()))
                        : "OK";

                lastAttempt = WebhookDeliveryAttempt.builder()
                        .attemptId(attemptId)
                        .endpointId(endpoint.getEndpointId())
                        .merchantId(endpoint.getMerchantId())
                        .eventType(eventType)
                        .payload(payloadJson)
                        .httpStatusCode(statusCode)
                        .attemptNumber(attemptNumber)
                        .success(true)
                        .responseBody(body)
                        .build();

                attemptRepository.save(lastAttempt);
                delivered = true;
                log.info("Webhook successfully delivered to {} on attempt {}", endpoint.getTargetUrl(), attemptNumber);

            } catch (Exception e) {
                String errorMsg = e.getMessage() != null ? e.getMessage() : "Connection failed";
                log.warn("Webhook attempt {} failed for endpoint {}: {}", attemptNumber, endpoint.getEndpointId(), errorMsg);

                lastAttempt = WebhookDeliveryAttempt.builder()
                        .attemptId(attemptId)
                        .endpointId(endpoint.getEndpointId())
                        .merchantId(endpoint.getMerchantId())
                        .eventType(eventType)
                        .payload(payloadJson)
                        .httpStatusCode(500)
                        .attemptNumber(attemptNumber)
                        .success(false)
                        .errorMessage(errorMsg.length() > 490 ? errorMsg.substring(0, 490) : errorMsg)
                        .build();

                attemptRepository.save(lastAttempt);

                if (backoffScheduler.canRetry(attemptNumber)) {
                    long delay = backoffScheduler.getBackoffDelayMs(attemptNumber + 1);
                    log.info("Scheduling backoff delay {} ms before retry...", delay);
                    // In synchronous loop, minor sleep or proceed
                    try {
                        Thread.sleep(Math.min(delay, 500L));
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }
                attemptNumber++;
            }
        }

        // If all attempts failed, send to Dead Letter Queue (DLQ)
        if (!delivered && lastAttempt != null) {
            log.error("All {} attempts failed for webhook endpoint {}. Routing to Dead-Letter Queue.",
                    ExponentialBackoffScheduler.MAX_ATTEMPTS, endpoint.getEndpointId());

            DeadLetterWebhookRecord dlqRecord = DeadLetterWebhookRecord.builder()
                    .dlqId(UUID.randomUUID())
                    .endpointId(endpoint.getEndpointId())
                    .merchantId(endpoint.getMerchantId())
                    .eventType(eventType)
                    .payload(payloadJson)
                    .failureReason(lastAttempt.getErrorMessage())
                    .totalAttempts(ExponentialBackoffScheduler.MAX_ATTEMPTS)
                    .replayed(false)
                    .build();

            dlqRepository.save(dlqRecord);
        }

        return mapToAttemptResponse(lastAttempt);
    }

    private WebhookDeliveryAttemptResponse mapToAttemptResponse(WebhookDeliveryAttempt attempt) {
        if (attempt == null) return null;
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
