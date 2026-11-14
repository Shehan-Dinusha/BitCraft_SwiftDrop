package com.swiftdrop.pricing.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.swiftdrop.contracts.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/geofence-zone")
@Tag(name = "Geofence Zone", description = "Geofence zone service status")
public class GeofenceZoneController {
    @GetMapping("/health")
    @Operation(summary = "Check geofence zone endpoint health")
    public ApiResponse<String> health() {
        return ApiResponse.success("GeofenceZoneController is ready");
    }
}
