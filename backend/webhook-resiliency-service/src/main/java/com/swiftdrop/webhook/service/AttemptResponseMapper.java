package com.swiftdrop.webhook.service;

import com.swiftdrop.webhook.domain.entity.WebhookDeliveryAttempt;
import com.swiftdrop.webhook.dto.WebhookDeliveryAttemptResponse;

/**
 * Maps a stored attempt to its API form. The stored {@code response_body} column is never read, and the error
 * text is always regenerated from the classification, so neither older rows (which hold remote response bodies
 * and raw exception messages) nor new ones can leak the endpoint's reply, URL or host through the API.
 */
final class AttemptResponseMapper {

    private AttemptResponseMapper() {
    }

    static WebhookDeliveryAttemptResponse toResponse(WebhookDeliveryAttempt attempt) {
        if (attempt == null) {
            return null;
        }
        DlqFailureClassifier.Classification failure = null;
        if (!attempt.isSuccess()) {
            failure = attempt.getErrorClass() != null
                    ? DlqFailureClassifier.describe(attempt.getErrorClass(), attempt.getHttpStatusCode())
                    : DlqFailureClassifier.classify(attempt.getErrorMessage());
        }
        return new WebhookDeliveryAttemptResponse(
                attempt.getAttemptId(),
                attempt.getEndpointId(),
                attempt.getMerchantId(),
                attempt.getEventType(),
                attempt.getHttpStatusCode(),
                attempt.getAttemptNumber(),
                attempt.isSuccess(),
                failure != null ? failure.errorClass() : null,
                failure != null ? failure.summary() : null,
                attempt.getLatencyMs(),
                attempt.getAttemptedAt()
        );
    }
}
