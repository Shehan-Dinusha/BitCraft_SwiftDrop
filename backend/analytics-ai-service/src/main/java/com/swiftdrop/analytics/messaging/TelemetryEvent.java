package com.swiftdrop.analytics.messaging;

import java.time.Instant;
import java.util.UUID;

/** Internal validated envelope; payload is one of the shared event records. */
public record TelemetryEvent(String eventType, UUID eventId, UUID orderId,
                             UUID correlationId, Instant occurredAt, Object payload) {
}
