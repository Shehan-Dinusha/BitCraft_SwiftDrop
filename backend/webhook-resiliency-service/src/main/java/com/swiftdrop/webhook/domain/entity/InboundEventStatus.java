package com.swiftdrop.webhook.domain.entity;

/**
 * Merchant-resolution state of an {@link InboundEvent}. Mirrors {@code ck_inbound_events_status}.
 */
public enum InboundEventStatus {
    /** The owning order's {@code order.created} has not been ingested yet. Resolved automatically when it is. */
    AWAITING_MERCHANT,
    /** Merchant known; one delivery was created per matching active endpoint (possibly none). */
    RESOLVED,
    /** The order belongs to no merchant (a customer order), so there is nothing to deliver. */
    NO_MERCHANT
}
