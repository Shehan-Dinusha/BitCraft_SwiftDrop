package com.swiftdrop.dispatch.dto;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.dispatch.domain.entity.CourierShift;
import com.swiftdrop.dispatch.domain.enums.ShiftStatus;
import com.swiftdrop.dispatch.domain.enums.VehicleType;
import com.swiftdrop.dispatch.service.CourierLoad;
import com.swiftdrop.dispatch.service.WorkHours;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Details of courier shift, availability, and capacity.")
public record CourierShiftResponse(
        @Schema(description = "Courier UUID")
        UUID courierId,

        @Schema(description = "Courier name")
        String courierName,

        @Schema(description = "Courier contact phone")
        String phone,

        @Schema(description = "Vehicle type")
        VehicleType vehicleType,

        @Schema(description = "Current assigned district")
        SriLankanDistrict currentDistrict,

        @Schema(description = "Current shift status")
        ShiftStatus shiftStatus,

        @Schema(description = "Currently active assigned parcels count")
        Integer activeParcelsCount,

        @Schema(description = "Maximum concurrent parcel capacity")
        Integer maxCapacity,

        @Schema(description = "Shift start timestamp")
        Instant shiftStartedAt,

        @Schema(description = "Shift end timestamp")
        Instant shiftEndedAt,

        @Schema(description = "Last heartbeat timestamp")
        Instant lastHeartbeatAt,

        @Schema(description = "Weight of the parcels held, in kg")
        BigDecimal activeWeightKg,

        @Schema(description = "Weight the vehicle carries, in kg")
        Integer maxWeightKg,

        @Schema(description = "Volume of the parcels held, in litres")
        BigDecimal activeVolumeLitres,

        @Schema(description = "Volume the vehicle carries, in litres")
        Integer maxVolumeLitres,

        @Schema(description = "When the lunch break of this shift started; null before it")
        Instant breakStartedAt,

        @Schema(description = "When that break should end, 45 minutes after it started; null before it")
        Instant breakEndsAt,

        @Schema(description = "When that break ended; null while on it or before it")
        Instant breakEndedAt,

        @Schema(description = "Whether the shift started after 9:00")
        boolean startedLate,

        @Schema(description = "When the courier left the hub on their run; null while at the hub. "
                + "Hub parcels go only to couriers at the hub.")
        Instant runStartedAt,

        @Schema(description = "When the courier last came back to the hub from a run this shift; null before")
        Instant lastRunEndedAt,

        @Schema(description = "Whether the courier left the shift open and it was closed at night")
        boolean closedAutomatically
) {
    public static CourierShiftResponse fromEntity(CourierShift entity, CourierLoad load, WorkHours hours) {
        return new CourierShiftResponse(
                entity.getCourierId(),
                entity.getCourierName(),
                entity.getPhone(),
                entity.getVehicleType(),
                entity.getCurrentDistrict(),
                entity.getShiftStatus(),
                entity.getActiveParcelsCount(),
                entity.getMaxCapacity(),
                entity.getShiftStartedAt(),
                entity.getShiftEndedAt(),
                entity.getLastHeartbeatAt(),
                load.weightKg(),
                entity.getVehicleType().maxWeightKg(),
                load.volumeLitres(),
                entity.getVehicleType().maxVolumeLitres(),
                entity.getBreakStartedAt(),
                hours.breakEndsAt(entity.getBreakStartedAt()),
                entity.getBreakEndedAt(),
                hours.startedLate(entity.getShiftStartedAt()),
                entity.getRunStartedAt(),
                entity.getLastRunEndedAt(),
                entity.isClosedAutomatically()
        );
    }
}
