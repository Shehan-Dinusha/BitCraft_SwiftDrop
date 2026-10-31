package com.swiftdrop.gateway.trackinghub.dto;

import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * PII-free parcel tracking view for public clients.
 * Only tracking number, public status, districts, and the public timeline are
 * exposed; courier, merchant, customer, contact, address, and pricing data are
 * never included.
 */
@Schema(name = "PublicTrackingView", description = "PII-free parcel tracking view for public clients.")
public record PublicTrackingViewDto(
        @Schema(description = "Public parcel tracking number.", example = "SD-2026-000123")
        String trackingNumber,
        @Schema(description = "Current public parcel status.", example = "IN_TRANSIT")
        PublicOrderStatus currentPublicStatus,
        @Schema(description = "District where the parcel entered the network.", example = "COLOMBO")
        SriLankanDistrict originDistrict,
        @Schema(description = "Destination district.", example = "KANDY")
        SriLankanDistrict destinationDistrict,
        @Schema(description = "Chronological public status timeline.")
        List<PublicTrackingTimelineEntry> statusTimeline
) {
}
