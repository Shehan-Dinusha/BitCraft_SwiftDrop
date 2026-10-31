package com.swiftdrop.dispatch.dto;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Location heartbeat ping from courier mobile device.")
public record CourierLocationPingRequest(
        @NotNull(message = "Courier ID is required")
        @Schema(description = "Courier UUID")
        UUID courierId,

        @NotNull(message = "District is required")
        @Schema(description = "Current district", example = "COLOMBO")
        SriLankanDistrict district,

        @Schema(description = "GPS Latitude", example = "6.9271")
        BigDecimal latitude,

        @Schema(description = "GPS Longitude", example = "79.8612")
        BigDecimal longitude
) {}
