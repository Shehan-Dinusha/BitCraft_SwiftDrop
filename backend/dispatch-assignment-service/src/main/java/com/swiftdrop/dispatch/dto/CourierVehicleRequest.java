package com.swiftdrop.dispatch.dto;

import com.swiftdrop.dispatch.domain.enums.VehicleType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "A courier's own vehicle. The courier registers it once; afterwards only their dispatcher can change it.")
public record CourierVehicleRequest(
        @NotNull(message = "Vehicle type is required")
        @Schema(description = "Vehicle type", example = "MOTORCYCLE")
        VehicleType vehicleType,

        @NotBlank(message = "Number plate is required")
        @Size(max = 20)
        @Schema(description = "Number plate as shown on the vehicle; stored in upper case, e.g. WP BAB-1234",
                example = "WP BAB-1234")
        String plateNumber,

        @NotBlank(message = "Make and model are required")
        @Size(max = 64)
        @Schema(description = "Make and model", example = "Honda Dio")
        String vehicleModel,

        @NotBlank(message = "Colour is required")
        @Size(max = 32)
        @Schema(description = "Vehicle colour", example = "Black")
        String vehicleColour,

        @NotBlank(message = "Driving licence number is required")
        @Size(max = 20)
        @Schema(description = "Driving licence number", example = "B1234567")
        String licenceNumber,

        @Min(1)
        @Max(200)
        @Schema(description = "Parcels the courier carries at once. Only a dispatcher can set it; when left out it "
                + "defaults to the vehicle type's capacity (motorcycle 5, three-wheeler 10, van 25, lorry 50).",
                example = "5")
        Integer maxCapacity
) {
}
