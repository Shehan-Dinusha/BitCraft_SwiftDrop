package com.swiftdrop.dispatch.dto;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.dispatch.domain.entity.CourierShift;
import com.swiftdrop.dispatch.domain.enums.ShiftStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Current active workload and availability for a courier.")
public record CourierWorkloadResponse(
        @Schema(description = "Courier UUID")
        UUID courierId,

        @Schema(description = "Courier display name")
        String courierName,

        @Schema(description = "Current active assignment count")
        int activeParcels,

        @Schema(description = "Maximum concurrent assignments")
        int maxCapacity,

        @Schema(description = "Current courier district")
        SriLankanDistrict currentDistrict,

        @Schema(description = "Current shift status")
        ShiftStatus shiftStatus
) {
    public static CourierWorkloadResponse fromEntity(CourierShift courier) {
        return new CourierWorkloadResponse(
                courier.getCourierId(),
                courier.getCourierName(),
                courier.getActiveParcelsCount(),
                courier.getMaxCapacity(),
                courier.getCurrentDistrict(),
                courier.getShiftStatus()
        );
    }
}
