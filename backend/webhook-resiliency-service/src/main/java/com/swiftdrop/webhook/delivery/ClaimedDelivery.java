package com.swiftdrop.webhook.delivery;

import java.time.Instant;
import java.util.UUID;

/**
 * A delivery job leased to one worker for exactly one attempt. {@code (deliveryId, leaseOwner, attemptNumber)} is the
 * fence: the attempt's result is only stored if the row is still {@code IN_FLIGHT} with this owner and this
 * attempt number ({@link DeliveryAttemptRecorder#record}).
 *
 * @param attemptNumber the attempt this lease is for (= {@code attempt_count} after the claim incremented it)
 */
public record ClaimedDelivery(UUID deliveryId, UUID inboundEventId, UUID endpointId, UUID merchantId,
                              String eventType, int attemptNumber, String leaseOwner, Instant leaseExpiresAt) {
}
