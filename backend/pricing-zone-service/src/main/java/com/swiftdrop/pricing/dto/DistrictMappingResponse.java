package com.swiftdrop.pricing.dto;

import com.swiftdrop.pricing.domain.entity.DistrictZoneMapping;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "District to zone mapping")
public record DistrictMappingResponse(
        @Schema(description = "District name in upper case", example = "KANDY")
        String district,
        @Schema(description = "Code of the zone the district belongs to", example = "ZONE_CENTRAL")
        String zoneCode) {

    public static DistrictMappingResponse from(DistrictZoneMapping mapping) {
        return new DistrictMappingResponse(mapping.getDistrict(), mapping.getZoneCode());
    }
}
