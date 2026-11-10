package com.swiftdrop.contracts.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event published by {@code merchant-b2b-service} when a merchant account is approved
 * by a platform administrator.
 *
 * <p>Consumers:
 * <ul>
 *   <li>{@code auth-iam-service} — links the merchant's {@code UserAccount.merchantId} so the
 *       IAM token can carry a {@code merchantId} claim.</li>
 *   <li>{@code notification-service} — sends a "Welcome, merchant!" email/push.</li>
 * </ul>
 *
 * <p>Routing key: {@link EventRoutingKeys#MERCHANT_APPROVED} ({@code "merchant.approved"}).<br>
 * Exchange: {@code swiftdrop.events.exchange} (topic).
 *
 * @param eventId    globally unique event identifier used for idempotent processing
 * @param merchantId the platform-assigned merchant identifier (UUID)
 * @param userId     the IAM user UUID associated with the approved merchant account
 * @param occurredAt wall-clock timestamp when the approval occurred
 */
public record MerchantApprovedEvent(
        UUID    eventId,
        UUID    merchantId,
        UUID    userId,
        Instant occurredAt
) {}

