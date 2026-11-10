package com.swiftdrop.contracts.dto.tracking;

import com.swiftdrop.contracts.enums.PublicOrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * A status the order reached and when.
 */
@Schema(description = "A status the order reached and when.")
public record HistoryEntry(

        @Schema(description = "Public status.", example = "PICKED_UP")
        PublicOrderStatus status,

        @Schema(description = "When the order reached this status (UTC).", example = "2026-10-30T10:15:30Z")
        Instant occurredAt
) {
}