package com.swiftdrop.delivery.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "GPS telemetry breadcrumb submitted by active courier device.")
public record CourierLocationUpdatePayload(
        @NotNull(message = "Latitude is required")
        @Schema(description = "GPS Latitude", example = "6.9271")
        double latitude,

        @NotNull(message = "Longitude is required")
        @Schema(description = "GPS Longitude", example = "79.8612")
        double longitude,

        @Schema(description = "Heading bearing degrees (0-360)", example = "45.0")
        double bearingDegrees,

        @Schema(description = "Vehicle speed in km/h", example = "35.5")
        double speedKmh,

        @Schema(description = "GPS accuracy radius in meters", example = "4.2")
        double accuracy
) {}
