package com.swiftdrop.dispatch.controller;

import com.swiftdrop.dispatch.security.DistrictScope;
import com.swiftdrop.contracts.security.CallerContext;
import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.dispatch.domain.enums.ShiftStatus;
import com.swiftdrop.dispatch.dto.CourierShiftResponse;
import com.swiftdrop.dispatch.dto.CourierWorkloadResponse;
import com.swiftdrop.dispatch.dto.StartShiftRequest;
import com.swiftdrop.dispatch.dto.UpdateShiftStatusRequest;
import com.swiftdrop.dispatch.security.DispatchAccess;
import com.swiftdrop.dispatch.service.CourierShiftService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
    @Operation(summary = "List couriers with optional district and shift status filters",
            description = "A DISPATCHER gets the couriers of their own district (the district parameter is "
                    + "ignored); a SYSTEM_ADMIN gets every district or the one given.")
    public ApiResponse<List<CourierShiftResponse>> getCouriers(
            @RequestParam(required = false) SriLankanDistrict district,
            @RequestParam(required = false) ShiftStatus status,
            HttpServletRequest httpRequest
    ) {
        CallerContext caller = DispatchAccess.requireOperations(httpRequest);
        return ApiResponse.success(
                courierShiftService.getCouriers(DistrictScope.listDistrict(caller, district), status),
                "Couriers retrieved"
        );
    }

    @GetMapping("/{courierId}")
    @Operation(summary = "Get courier shift status and live metrics",
            description = "A COURIER may use only their own courier ID; DISPATCHER and SYSTEM_ADMIN may use any.")
    public ApiResponse<CourierShiftResponse> getCourier(
            @PathVariable UUID courierId,
            HttpServletRequest httpRequest) {
        requireCourierAccess(httpRequest, courierId);
        return ApiResponse.success(
                courierShiftService.getCourier(courierId),
                "Courier retrieved"
        );
    }

    @GetMapping("/workload")
    @Operation(summary = "List current courier workloads",
            description = "A DISPATCHER sees their own district's couriers; a SYSTEM_ADMIN sees all.")
    public ApiResponse<List<CourierWorkloadResponse>> getWorkloads(HttpServletRequest httpRequest) {
        CallerContext caller = DispatchAccess.requireOperations(httpRequest);
        return ApiResponse.success(
                courierShiftService.getWorkloads(DistrictScope.listDistrict(caller, null)),
                "Courier workloads retrieved"
        );
    }

    @PostMapping("/shifts/start")
    @Operation(summary = "Start or register courier shift",
            description = "A COURIER may use only their own courier ID; DISPATCHER and SYSTEM_ADMIN may use any.")
    public ApiResponse<CourierShiftResponse> startShift(
            @Valid @RequestBody StartShiftRequest request,
            HttpServletRequest httpRequest) {
        DispatchAccess.requireCourierSelfOrOperations(httpRequest, request.courierId());
        return ApiResponse.success(
                courierShiftService.startShift(request),
                "Courier shift started successfully"
        );
    }

    @PostMapping("/shifts/{courierId}/end")
    @Operation(summary = "End courier shift",
            description = "A COURIER may use only their own courier ID; DISPATCHER and SYSTEM_ADMIN may use any.")
    public ApiResponse<CourierShiftResponse> endShift(
            @PathVariable UUID courierId,
            HttpServletRequest httpRequest) {
        requireCourierAccess(httpRequest, courierId);
        return ApiResponse.success(
                courierShiftService.endShift(courierId),
                "Courier shift ended successfully"
        );
    }

    @PutMapping("/shifts/status")
    @Operation(summary = "Update courier shift status (e.g., AVAILABLE, ON_BREAK, BUSY, OFFLINE)",
            description = "A COURIER may use only their own courier ID; DISPATCHER and SYSTEM_ADMIN may use any.")
    public ApiResponse<CourierShiftResponse> updateShiftStatus(
            @Valid @RequestBody UpdateShiftStatusRequest request,
            HttpServletRequest httpRequest) {
        requireCourierAccess(httpRequest, request.courierId());
        return ApiResponse.success(
                courierShiftService.updateStatus(request.courierId(), request.status()),
                "Shift status updated successfully"
        );
    }

    /** The courier themselves, a SYSTEM_ADMIN, or the dispatcher of the district the courier works in. */
    private CallerContext requireCourierAccess(HttpServletRequest httpRequest, UUID courierId) {
        CallerContext caller = DispatchAccess.requireCourierSelfOrOperations(httpRequest, courierId);
        if (caller.role() == com.swiftdrop.contracts.enums.UserRole.DISPATCHER) {
            DistrictScope.requireCourier(caller, courierShiftService.getCourier(courierId).currentDistrict());
        }
        return caller;
    }
}
