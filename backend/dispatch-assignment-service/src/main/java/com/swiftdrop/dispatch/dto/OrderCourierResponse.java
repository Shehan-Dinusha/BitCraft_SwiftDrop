package com.swiftdrop.dispatch.dto;

import com.swiftdrop.dispatch.domain.enums.VehicleType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "What a customer may see about the courier handling their parcel, so they can recognise "
        + "them at the door. No phone number, licence or courier id.")
public record OrderCourierResponse(
        @Schema(description = "Courier's first name", example = "Kasun")
        String firstName,
        @Schema(description = "Vehicle type; null if the courier has no vehicle on record")
        VehicleType vehicleType,
        @Schema(description = "Number plate", example = "WP BAB-1234")
        String plateNumber,
        @Schema(description = "Make and model", example = "Honda Dio")
        String vehicleModel,
        @Schema(description = "Vehicle colour", example = "Black")
        String vehicleColour
) {
}
