package com.swiftdrop.dispatch.dto;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.dispatch.domain.entity.CourierShift;
import com.swiftdrop.dispatch.domain.enums.CourierBlock;
import com.swiftdrop.dispatch.domain.enums.ShiftStatus;
import com.swiftdrop.dispatch.domain.enums.VehicleType;
import com.swiftdrop.dispatch.service.CourierLoad;
import com.swiftdrop.dispatch.service.WorkHours;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "A courier on shift, and whether they can take the order.")
public record CourierSuggestionResponse(
        @Schema(description = "Courier UUID")
        UUID courierId,

        @Schema(description = "Courier display name")
        String courierName,

        @Schema(description = "Courier's current district")
        SriLankanDistrict currentDistrict,

        @Schema(description = "Whether the courier is in the district handling the order")
        boolean samePickupDistrict,

        @Schema(description = "Current active assignment count")
        int activeParcels,

        @Schema(description = "Maximum concurrent assignments")
        int maxCapacity,

        @Schema(description = "Current shift status")
        ShiftStatus shiftStatus,

        @Schema(description = "Courier's vehicle")
        VehicleType vehicleType,

        @Schema(description = "Weight of the parcels held, in kg")
        BigDecimal activeWeightKg,

        @Schema(description = "Weight the vehicle carries, in kg")
        int maxWeightKg,

        @Schema(description = "Volume of the parcels held, in litres")
        BigDecimal activeVolumeLitres,

        @Schema(description = "Volume the vehicle carries, in litres")
        int maxVolumeLitres,

        @Schema(description = "Longest side of a parcel the vehicle takes, in cm")
        int maxSideCm,

        @Schema(description = "Whether the order can be assigned to this courier now")
        boolean eligible,

        @Schema(description = "Why not, when it cannot; null otherwise")
        CourierBlock ineligibleReason,

        @Schema(description = "While the courier is on their lunch break, when it should end; null otherwise. "
                + "They can still be given the order and see it when they are back.")
        Instant breakEndsAt
) {
    public static CourierSuggestionResponse of(CourierShift courier, CourierLoad load, CourierBlock block,
                                               SriLankanDistrict orderDistrict, WorkHours hours) {
        VehicleType vehicle = courier.getVehicleType();
        return new CourierSuggestionResponse(
                courier.getCourierId(),
                courier.getCourierName(),
                courier.getCurrentDistrict(),
                courier.getCurrentDistrict() == orderDistrict,
                courier.getActiveParcelsCount(),
                courier.getMaxCapacity(),
                courier.getShiftStatus(),
                vehicle,
                load.weightKg(),
                vehicle.maxWeightKg(),
                load.volumeLitres(),
                vehicle.maxVolumeLitres(),
                vehicle.maxSideCm(),
                block == null,
                block,
                breakEndsAt(courier, hours)
        );
    }

    /** When the courier's break should end, while they are on it. */
    public static Instant breakEndsAt(CourierShift courier, WorkHours hours) {
        return courier.getShiftStatus() == ShiftStatus.ON_BREAK
                ? hours.breakEndsAt(courier.getBreakStartedAt()) : null;
    }
}
