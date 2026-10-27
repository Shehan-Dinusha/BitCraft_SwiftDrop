package com.swiftdrop.dispatch.controller;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.dispatch.domain.enums.ShiftStatus;
import com.swiftdrop.dispatch.dto.CourierLocationPingRequest;
import com.swiftdrop.dispatch.dto.CourierShiftResponse;
import com.swiftdrop.dispatch.dto.StartShiftRequest;
import com.swiftdrop.dispatch.dto.UpdateShiftStatusRequest;
import com.swiftdrop.dispatch.service.CourierShiftService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dispatch/couriers")
@Tag(name = "CourierAvailability", description = "Courier shifts, capacity, and live availability tracking")
public class CourierAvailabilityController {

    private final CourierShiftService courierShiftService;

    public CourierAvailabilityController(CourierShiftService courierShiftService) {
        this.courierShiftService = courierShiftService;
    }

    @GetMapping("/health")
    @Operation(summary = "Check courier availability service health")
    public ApiResponse<String> health() {
        return ApiResponse.success("UP", "CourierAvailabilityController is ready");
    }

    @GetMapping
    @Operation(summary = "List couriers with optional district and shift status filters")
    public ApiResponse<List<CourierShiftResponse>> getCouriers(
            @RequestParam(required = false) SriLankanDistrict district,
            @RequestParam(required = false) ShiftStatus status
    ) {
        return ApiResponse.success(
                courierShiftService.getCouriers(district, status),
                "Couriers retrieved"
        );
    }

    @GetMapping("/{courierId}")
    @Operation(summary = "Get courier shift status and live metrics")
    public ApiResponse<CourierShiftResponse> getCourier(@PathVariable UUID courierId) {
        return ApiResponse.success(
                courierShiftService.getCourier(courierId),
                "Courier retrieved"
        );
    }

    @PostMapping("/shifts/start")
    @Operation(summary = "Start or register courier shift")
    public ApiResponse<CourierShiftResponse> startShift(@Valid @RequestBody StartShiftRequest request) {
        return ApiResponse.success(
                courierShiftService.startShift(request),
                "Courier shift started successfully"
        );
    }

    @PostMapping("/shifts/{courierId}/end")
    @Operation(summary = "End courier shift")
    public ApiResponse<CourierShiftResponse> endShift(@PathVariable UUID courierId) {
        return ApiResponse.success(
                courierShiftService.endShift(courierId),
                "Courier shift ended successfully"
        );
    }

    @PutMapping("/shifts/status")
    @Operation(summary = "Update courier shift status (e.g., AVAILABLE, ON_BREAK, BUSY, OFFLINE)")
    public ApiResponse<CourierShiftResponse> updateShiftStatus(@Valid @RequestBody UpdateShiftStatusRequest request) {
        return ApiResponse.success(
                courierShiftService.updateStatus(request.courierId(), request.status()),
                "Shift status updated successfully"
        );
    }

    @PutMapping("/shifts/location")
    @Operation(summary = "Record courier GPS location heartbeat ping")
    public ApiResponse<CourierShiftResponse> updateLocation(@Valid @RequestBody CourierLocationPingRequest request) {
        return ApiResponse.success(
                courierShiftService.updateLocation(request),
                "Location updated successfully"
        );
    }
}
