package com.swiftdrop.pricing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Assign a district to a pricing zone")
public record DistrictMappingRequest(
        @Schema(description = "Code of an existing zone to map the district to", example = "ZONE_CENTRAL")
        @NotBlank @Size(max = 32) String zoneCode) {
}
