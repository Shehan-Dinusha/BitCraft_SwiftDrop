package com.swiftdrop.dispatch.dto;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.dispatch.domain.entity.CourierShift;
import com.swiftdrop.dispatch.domain.enums.ShiftStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Courier suggestion for an order assignment.")
public record CourierSuggestionResponse(
        @Schema(description = "Courier UUID")
        UUID courierId,

        @Schema(description = "Courier display name")
        String courierName,

        @Schema(description = "Courier's current district")
        SriLankanDistrict currentDistrict,

        @Schema(description = "Whether the courier is in the order pickup district")
        boolean samePickupDistrict,

        @Schema(description = "Current active assignment count")
        int activeParcels,

        @Schema(description = "Maximum concurrent assignments")
        int maxCapacity,

        @Schema(description = "Current shift status")
        ShiftStatus shiftStatus
) {
    public static CourierSuggestionResponse fromEntity(CourierShift courier, SriLankanDistrict pickupDistrict) {
        return new CourierSuggestionResponse(
                courier.getCourierId(),
                courier.getCourierName(),
                courier.getCurrentDistrict(),
                courier.getCurrentDistrict() == pickupDistrict,
                courier.getActiveParcelsCount(),
                courier.getMaxCapacity(),
                courier.getShiftStatus()
        );
    }
}
