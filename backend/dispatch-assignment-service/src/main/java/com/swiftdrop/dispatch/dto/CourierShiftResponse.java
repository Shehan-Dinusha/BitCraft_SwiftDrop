package com.swiftdrop.dispatch.dto;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.dispatch.domain.entity.CourierShift;
import com.swiftdrop.dispatch.domain.enums.ShiftStatus;
import com.swiftdrop.dispatch.domain.enums.VehicleType;
import com.swiftdrop.dispatch.service.CourierLoad;
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
        Integer maxVolumeLitres
) {
    public static CourierShiftResponse fromEntity(CourierShift entity, CourierLoad load) {
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
                entity.getVehicleType().maxVolumeLitres()
        );
    }
}
