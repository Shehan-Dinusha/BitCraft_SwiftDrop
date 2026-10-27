package com.swiftdrop.dispatch.dto;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.dispatch.domain.enums.VehicleType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Request to start or register a courier shift.")
public record StartShiftRequest(
        @NotNull(message = "Courier ID is required")
        @Schema(description = "Courier UUID")
        UUID courierId,

        @NotBlank(message = "Courier name is required")
        @Schema(description = "Full name of the courier", example = "Sunil Perera")
        String courierName,

        @Schema(description = "Contact phone number", example = "+94771234567")
        String phone,

        @Schema(description = "Vehicle type", example = "MOTORCYCLE")
        VehicleType vehicleType,

        @NotNull(message = "Initial district is required")
        @Schema(description = "Starting district for shift", example = "COLOMBO")
        SriLankanDistrict initialDistrict,

        @Schema(description = "Maximum concurrent parcel capacity", example = "5")
        Integer maxCapacity
) {}
