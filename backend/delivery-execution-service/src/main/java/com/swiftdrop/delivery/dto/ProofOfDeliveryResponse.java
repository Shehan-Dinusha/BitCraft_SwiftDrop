package com.swiftdrop.delivery.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Persisted proof-of-delivery status.")
public record ProofOfDeliveryResponse(
        @Schema(example = "42") Long id,
        @Schema(example = "b8f2d1df-3d50-4f42-b6a3-6b6b96e5f0ca") UUID deliveryId,
        @Schema(example = "true") boolean verified,
        @Schema(example = "2026-10-22T12:00:00Z") Instant createdAt,
        @Schema(example = "2026-10-22T12:02:00Z") Instant verifiedAt
) {
}
