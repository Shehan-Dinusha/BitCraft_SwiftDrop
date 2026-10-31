package com.swiftdrop.gateway.trackinghub.dto;

import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * A single public tracking milestone. No staff, courier, or customer identity
 * is included.
 */
@Schema(name = "PublicTrackingTimelineEntry", description = "Public parcel status milestone.")
public record PublicTrackingTimelineEntry(
        @Schema(description = "Public status at this milestone.", example = "AT_HUB")
        PublicOrderStatus status,
        @Schema(description = "District where the milestone occurred.", example = "KANDY")
        SriLankanDistrict district,
        @Schema(description = "Time at which the milestone was recorded.", example = "2026-10-26T12:00:00Z")
        Instant timestamp
) {
}
