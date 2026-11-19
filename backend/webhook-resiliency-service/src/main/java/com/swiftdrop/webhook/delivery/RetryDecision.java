package com.swiftdrop.webhook.delivery;

import com.swiftdrop.webhook.domain.entity.AttemptErrorClass;
import com.swiftdrop.webhook.domain.entity.WebhookDeliveryStatus;

import java.time.Duration;
import java.time.Instant;

/**
 * What to do with a delivery job after one attempt ({@link DeliveryRetryPolicy}). Maps one-to-one onto the existing
 * {@code webhook_deliveries} columns, which Step 9 updates in the same transaction as the attempt row:
 *
 * <pre>
 *   SUCCEEDED → status = SUCCEEDED, next_attempt_at = NULL
 *   RETRY     → status = RETRY,     next_attempt_at = {@link #nextAttemptAt} (non-null, ck_webhook_deliveries_schedule)
 *   DEAD      → status = DEAD,      next_attempt_at = NULL
 * </pre>
 * {@code attempt_count} is not changed here: it already equals {@link #attemptNumber}, the attempt just made.
 *
 * @param attemptNumber  1-based number of the attempt that produced the outcome
 * @param nextAttemptAt  when the next attempt may run; RETRY only, otherwise null
 * @param delay          the delay that was applied; RETRY only, otherwise null
 * @param retryAfterUsed true if a valid, capped {@code Retry-After} was longer than the normal backoff and was used
 * @param errorClass     the attempt's error class; null for SUCCEEDED
 * @param deadReason     DEAD only, otherwise null
 */
public record RetryDecision(Kind kind, int attemptNumber, Instant nextAttemptAt, Duration delay,
                            boolean retryAfterUsed, AttemptErrorClass errorClass, DeadReason deadReason) {

    public enum Kind {
        SUCCEEDED,
        RETRY,
        DEAD
    }

    public enum DeadReason {
        /** Non-retryable outcome (3xx, other 4xx, blocked destination): no further attempt. */
        PERMANENT_FAILURE,
        /** The last allowed attempt failed. */
        ATTEMPTS_EXHAUSTED
    }

    /** The {@code webhook_deliveries.status} this decision sets. */
    public WebhookDeliveryStatus deliveryStatus() {
        return switch (kind) {
            case SUCCEEDED -> WebhookDeliveryStatus.SUCCEEDED;
            case RETRY -> WebhookDeliveryStatus.RETRY;
            case DEAD -> WebhookDeliveryStatus.DEAD;
        };
    }
}
