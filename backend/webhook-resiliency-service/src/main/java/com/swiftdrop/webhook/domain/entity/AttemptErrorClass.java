package com.swiftdrop.webhook.domain.entity;

/**
 * Classification of a failed delivery attempt. Mirrors {@code ck_webhook_delivery_attempts_error_class}.
 * Populated by the delivery worker; legacy attempts have no classification.
 */
public enum AttemptErrorClass {
    TIMEOUT,
    CONNECTION_FAILURE,
    HTTP_CLIENT_ERROR,
    HTTP_RATE_LIMITED,
    HTTP_SERVER_ERROR,
    INVALID_RESPONSE,
    BLOCKED_DESTINATION,
    UNKNOWN
}
