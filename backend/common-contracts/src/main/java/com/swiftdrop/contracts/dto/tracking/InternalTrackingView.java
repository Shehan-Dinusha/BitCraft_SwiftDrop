package com.swiftdrop.contracts.dto.tracking;

import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Public-safe tracking view for service-to-service use (the gateway tracking hub).
 * It deliberately carries masked contact only; no raw name, phone, street address, email,
 * customer or merchant id, or fee.
 */
@Schema(description = "Public-safe tracking view: status, status history, city/district and masked contact only.")
public record InternalTrackingView(

        @Schema(description = "Tracking number.", example = "SD-7K4M-9QX2")
        String trackingNumber,

        @Schema(description = "Current public status.", example = "IN_TRANSIT")
        PublicOrderStatus status,

        @Schema(description = "Status history, oldest first.")
        List<HistoryEntry> history,

        @Schema(description = "Pickup city.", example = "Colombo 03")
        String pickupCity,

        @Schema(description = "Pickup district.", example = "COLOMBO")
        SriLankanDistrict pickupDistrict,

        @Schema(description = "Drop-off city.", example = "Kandy")
        String deliveryCity,

        @Schema(description = "Drop-off district.", example = "KANDY")
        SriLankanDistrict deliveryDistrict,

        @Schema(description = "Masked sender name (initials only).", example = "N. P.")
        String senderNameMasked,

        @Schema(description = "Masked sender phone.", example = "07X***4567")
        String senderPhoneMasked,

        @Schema(description = "Masked receiver name (initials only).", example = "K. S.")
        String receiverNameMasked,

        @Schema(description = "Masked receiver phone.", example = "07X***1234")
        String receiverPhoneMasked
) {

    /** Without the masked contact fields (they are null), for callers built before they existed. */
    public InternalTrackingView(
            String trackingNumber,
            PublicOrderStatus status,
            List<HistoryEntry> history,
            String pickupCity,
            SriLankanDistrict pickupDistrict,
            String deliveryCity,
            SriLankanDistrict deliveryDistrict) {
        this(trackingNumber, status, history, pickupCity, pickupDistrict, deliveryCity, deliveryDistrict,
                null, null, null, null);
    }
}