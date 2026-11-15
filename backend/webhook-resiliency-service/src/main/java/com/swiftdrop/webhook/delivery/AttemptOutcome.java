package com.swiftdrop.webhook.delivery;

import com.swiftdrop.webhook.domain.entity.AttemptErrorClass;

/**
 * Result of one outbound attempt: safe, structured, and ready to store as a {@code webhook_delivery_attempts} row
 * (Steps 9–10). Contains no response body, URL, secret or exception text.
 *
 * @param httpStatus  status received, or null when no HTTP response arrived (timeout, connection failure, blocked)
 * @param errorClass  null on success (2xx)
 * @param retryable   decision D3: true for timeout, connection failure, 408, 425, 429 and 5xx; false for success,
 *                    3xx, other 4xx and blocked destinations. {@code UNKNOWN} failures are treated as retryable.
 *                    The retry policy (Step 8) decides the schedule.
 * @param latencyMs   wall time of the attempt, from just before signing to the outcome
 * @param retryAfter  raw {@code Retry-After} header (trimmed, at most 64 characters) on 429 and 503 responses, else
 *                    null. Parsed and capped by the retry policy (Step 8, decision D2).
 */
public record AttemptOutcome(Integer httpStatus, AttemptErrorClass errorClass, boolean retryable, int latencyMs,
                             String retryAfter) {

    public boolean success() {
        return errorClass == null;
    }
}
