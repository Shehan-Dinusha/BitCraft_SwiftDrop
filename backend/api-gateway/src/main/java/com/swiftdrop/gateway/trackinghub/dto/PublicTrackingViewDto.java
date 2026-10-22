package com.swiftdrop.gateway.trackinghub.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(name = "PublicTrackingView", description = "PII-free parcel tracking view for public clients.")
public record PublicTrackingViewDto(
        @Schema(description = "Public parcel tracking identifier.", example = "parcel-123")
        String trackingId,
        @Schema(description = "Current delivery state.", example = "IN_TRANSIT")
        String status,
        @Schema(description = "Approximate latitude, rounded by the tracking proxy.", example = "6.9271")
        Double latitude,
        @Schema(description = "Approximate longitude, rounded by the tracking proxy.", example = "79.8612")
        Double longitude,
        @Schema(description = "Current delivery milestones.")
        java.util.List<String> milestones,
        @Schema(description = "Last event time.", example = "2026-10-22T12:00:00Z")
        Instant updatedAt
) {
}
