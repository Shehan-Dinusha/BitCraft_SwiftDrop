package com.swiftdrop.pricing.controller;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.pricing.dto.DistrictMappingRequest;
import com.swiftdrop.pricing.dto.DistrictMappingResponse;
import com.swiftdrop.pricing.dto.ZoneRequest;
import com.swiftdrop.pricing.dto.ZoneResponse;
import com.swiftdrop.pricing.service.ZoneAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/zones")
@Tag(name = "Zone Management", description = "Manage pricing zones and district mappings")
@RequiredArgsConstructor
public class ZoneAdminController {

    private final ZoneAdminService zoneAdminService;

    @GetMapping
    @Operation(summary = "List pricing zones")
    public ApiResponse<List<ZoneResponse>> listZones() {
        return ApiResponse.success(zoneAdminService.listZones(), "Zones retrieved successfully");
    }

    @GetMapping("/{zoneCode}")
    @Operation(summary = "Get a pricing zone")
    public ApiResponse<ZoneResponse> getZone(@PathVariable String zoneCode) {
        return ApiResponse.success(zoneAdminService.getZone(zoneCode), "Zone retrieved successfully");
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a pricing zone")
    public ApiResponse<ZoneResponse> createZone(@Valid @RequestBody ZoneRequest request) {
        return ApiResponse.success(zoneAdminService.createZone(request), "Zone created successfully");
    }

    @PutMapping("/{zoneCode}")
    @Operation(summary = "Update a pricing zone")
    public ApiResponse<ZoneResponse> updateZone(@PathVariable String zoneCode,
                                                @Valid @RequestBody ZoneRequest request) {
        return ApiResponse.success(zoneAdminService.updateZone(zoneCode, request), "Zone updated successfully");
    }

    @DeleteMapping("/{zoneCode}")
    @Operation(summary = "Delete a pricing zone that has no districts mapped")
    public ApiResponse<Void> deleteZone(@PathVariable String zoneCode) {
        zoneAdminService.deleteZone(zoneCode);
        return ApiResponse.success(null, "Zone deleted successfully");
    }

    @GetMapping("/districts")
    @Operation(summary = "List district to zone mappings")
    public ApiResponse<List<DistrictMappingResponse>> listMappings() {
        return ApiResponse.success(zoneAdminService.listMappings(), "Mappings retrieved successfully");
    }

    @PutMapping("/districts/{district}")
    @Operation(summary = "Assign a district to a zone")
    public ApiResponse<DistrictMappingResponse> assignDistrict(@PathVariable String district,
                                                               @Valid @RequestBody DistrictMappingRequest request) {
        return ApiResponse.success(zoneAdminService.assignDistrict(district, request), "District mapping updated");
    }
}
