package com.swiftdrop.webhook.exception;

/**
 * An inbound event that can never be ingested as sent: retrying it would fail the same way. The listener's
 * retry policy rejects it straight to the broker dead-letter queue, where the original message is kept.
 *
 * <p>Messages must not include payload content or personal data; they are logged.</p>
 */
public class NonRetryableIngestionException extends RuntimeException {

    public NonRetryableIngestionException(String message) {
        super(message);
    }
}
