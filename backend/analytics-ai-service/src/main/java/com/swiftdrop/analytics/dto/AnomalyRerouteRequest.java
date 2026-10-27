package com.swiftdrop.analytics.dto;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request parameters for AI anomaly reroute suggestions")
public record AnomalyRerouteRequest(
        @NotBlank(message = "trackingNumber is required")
        @Schema(description = "Parcel tracking number", example = "SWD-20261027-COL-0001")
        String trackingNumber,

        @NotNull(message = "currentDistrict is required")
        @Schema(description = "Current district where parcel is delayed or located", example = "GAMPAHA")
        SriLankanDistrict currentDistrict,

        @NotNull(message = "destinationDistrict is required")
        @Schema(description = "Final delivery destination district", example = "JAFFNA")
        SriLankanDistrict destinationDistrict,

        @Schema(description = "Current detected transit delay in minutes", example = "45")
        int delayMinutes,

        @Schema(description = "Reported anomaly reason", example = "ROAD_CLOSURE_A9")
        String anomalyReason
) {}
