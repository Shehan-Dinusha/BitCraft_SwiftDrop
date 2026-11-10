package com.swiftdrop.contracts.dto.tracking;

import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Public-safe tracking view for service-to-service use (the gateway tracking hub).
 * It deliberately carries no street address, name, phone, email, customer or
 * merchant id, or fee.
 */
@Schema(description = "Public-safe tracking view: status, status history and city/district only.")
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
        SriLankanDistrict deliveryDistrict
) {
}