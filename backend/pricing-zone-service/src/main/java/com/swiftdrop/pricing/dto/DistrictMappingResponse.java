package com.swiftdrop.pricing.dto;

import com.swiftdrop.pricing.domain.entity.DistrictZoneMapping;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "District to zone mapping")
public record DistrictMappingResponse(String district, String zoneCode) {

    public static DistrictMappingResponse from(DistrictZoneMapping mapping) {
        return new DistrictMappingResponse(mapping.getDistrict(), mapping.getZoneCode());
    }
}
