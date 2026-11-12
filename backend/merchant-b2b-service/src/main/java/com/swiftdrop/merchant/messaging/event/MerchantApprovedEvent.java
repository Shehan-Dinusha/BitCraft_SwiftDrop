package com.swiftdrop.merchant.messaging.event;

import java.time.Instant;
import java.util.UUID;

/** Local PR 1 event until Member 9 supplies the shared contract (Member 7 guide, stage 5). */
public record MerchantApprovedEvent(UUID eventId, UUID correlationId, UUID merchantId,
                                    UUID ownerUserId, Instant approvedAt, Instant timestamp) {
    public static final String ROUTING_KEY = "merchant.approved";

    public MerchantApprovedEvent {
        if (eventId == null) eventId = UUID.randomUUID();
        if (correlationId == null) correlationId = UUID.randomUUID();
        if (timestamp == null) timestamp = Instant.now();
    }
}
