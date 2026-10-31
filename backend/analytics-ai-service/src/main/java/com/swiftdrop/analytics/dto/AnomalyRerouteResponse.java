package com.swiftdrop.analytics.dto;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "AI dynamic rerouting recommendations and transit bypass steps")
public record AnomalyRerouteResponse(
        @Schema(description = "Parcel tracking number", example = "SWD-20261027-COL-0001")
        String trackingNumber,

        @Schema(description = "Suggested transit hub or intermediate handoff", example = "KURUNEGALA")
        SriLankanDistrict suggestedTransitHub,

        @Schema(description = "Recommended expedited service level", example = "EXPRESS")
        String recommendedSpeed,

        @Schema(description = "Priority score from 1 (low) to 10 (critical emergency)", example = "8")
        int priorityScore,

        @Schema(description = "Step-by-step waypoint recommendations")
        List<String> suggestedSteps,

        @Schema(description = "Operational rationale for reroute suggestion", example = "Bypassing congested A9 corridor through Kurunegala hub reduces transit time by 35 minutes.")
        String rationale
) {}
