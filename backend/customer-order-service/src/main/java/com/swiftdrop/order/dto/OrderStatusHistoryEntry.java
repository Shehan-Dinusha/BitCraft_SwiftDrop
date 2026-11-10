package com.swiftdrop.order.dto;

import com.swiftdrop.contracts.enums.PublicOrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** One step in an order's status history, as shown to its owner. It carries no internal sub-status or event id. */
@Schema(description = "A status the order reached and when.")
public record OrderStatusHistoryEntry(

        @Schema(description = "Public status the order reached", example = "PICKED_UP")
        PublicOrderStatus status,

        @Schema(description = "When the order reached this status", example = "2026-11-05T08:30:00Z")
        Instant occurredAt
) {
}
