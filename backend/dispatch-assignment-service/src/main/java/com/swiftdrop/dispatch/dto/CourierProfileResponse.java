package com.swiftdrop.dispatch.dto;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.dispatch.domain.entity.CourierProfile;
import com.swiftdrop.dispatch.domain.enums.VehicleType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "A courier's registered vehicle and parcel capacity.")
public record CourierProfileResponse(
        @Schema(description = "Courier UUID (IAM user id)")
        UUID courierId,
        @Schema(description = "Courier's home district")
        SriLankanDistrict district,
        @Schema(description = "Vehicle type")
        VehicleType vehicleType,
        @Schema(description = "Number plate", example = "WP BAB-1234")
        String plateNumber,
        @Schema(description = "Make and model", example = "Honda Dio")
        String vehicleModel,
        @Schema(description = "Vehicle colour", example = "Black")
        String vehicleColour,
        @Schema(description = "Driving licence number", example = "B1234567")
        String licenceNumber,
        @Schema(description = "Parcels the courier carries at once")
        Integer maxCapacity,
        @Schema(description = "When the vehicle was registered")
        Instant createdAt,
        @Schema(description = "When the profile last changed")
        Instant updatedAt
) {
    public static CourierProfileResponse fromEntity(CourierProfile profile) {
        return new CourierProfileResponse(profile.getCourierId(), profile.getDistrict(), profile.getVehicleType(),
                profile.getPlateNumber(), profile.getVehicleModel(), profile.getVehicleColour(),
                profile.getLicenceNumber(), profile.getMaxCapacity(), profile.getCreatedAt(), profile.getUpdatedAt());
    }
}
