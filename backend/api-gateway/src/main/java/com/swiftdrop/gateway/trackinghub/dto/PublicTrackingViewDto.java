package com.swiftdrop.gateway.trackinghub.dto;

import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Public parcel tracking view for anonymous clients.
 * Only the tracking number, public status, city/district, the public timeline,
 * and already-masked contact details are exposed. Raw names, phone numbers,
 * street addresses, courier, merchant, customer, and pricing data are never
 * included.
 */
@Schema(name = "PublicTrackingView",
        description = "Public parcel tracking view: status, city/district, timeline, and masked contact only.")
public record PublicTrackingViewDto(
        @Schema(description = "Public parcel tracking number.", example = "SD-2026-000123")
        String trackingNumber,
        @Schema(description = "Current public parcel status.", example = "IN_TRANSIT")
        PublicOrderStatus currentPublicStatus,
        @Schema(description = "District where the parcel entered the network.", example = "COLOMBO")
        SriLankanDistrict originDistrict,
        @Schema(description = "Destination district.", example = "KANDY")
        SriLankanDistrict destinationDistrict,
        @Schema(description = "City where the parcel was handed over.", example = "Colombo 03")
        String originCity,
        @Schema(description = "Destination city.", example = "Kandy")
        String destinationCity,
        @Schema(description = "Sender name reduced to initials only.", example = "N. P.")
        String senderNameMasked,
        @Schema(description = "Sender phone with the middle digits hidden.", example = "07X***4567")
        String senderPhoneMasked,
        @Schema(description = "Receiver name reduced to initials only.", example = "K. S.")
        String receiverNameMasked,
        @Schema(description = "Receiver phone with the middle digits hidden.", example = "07X***1234")
        String receiverPhoneMasked,
        @Schema(description = "Chronological public status timeline.")
        List<PublicTrackingTimelineEntry> statusTimeline
) {

    /** Without city or contact details (they are null), for callers built before they existed. */
    public PublicTrackingViewDto(
            String trackingNumber,
            PublicOrderStatus currentPublicStatus,
            SriLankanDistrict originDistrict,
            SriLankanDistrict destinationDistrict,
            List<PublicTrackingTimelineEntry> statusTimeline) {
        this(trackingNumber, currentPublicStatus, originDistrict, destinationDistrict,
                null, null, null, null, null, null, statusTimeline);
    }
}
