package com.swiftdrop.dispatch.controller;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.security.CallerContext;
import com.swiftdrop.dispatch.dto.CourierProfileResponse;
import com.swiftdrop.dispatch.dto.CourierVehicleRequest;
import com.swiftdrop.dispatch.dto.OrderCourierResponse;
import com.swiftdrop.dispatch.service.CourierProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dispatch")
@Tag(name = "Courier vehicles", description = "Couriers' own registered vehicles and parcel capacity")
public class CourierProfileController {

    private final CourierProfileService courierProfileService;

    public CourierProfileController(CourierProfileService courierProfileService) {
        this.courierProfileService = courierProfileService;
    }

    @GetMapping("/couriers/me/profile")
    @Operation(summary = "Get my vehicle (COURIER)",
            description = "404 until the courier has registered their vehicle; a shift cannot start before that.")
    public ApiResponse<CourierProfileResponse> getMyProfile(HttpServletRequest httpRequest) {
        CallerContext caller = CallerContext.from(httpRequest::getHeader);
        return ApiResponse.success(courierProfileService.getOwn(caller), "Courier profile retrieved");
    }

    @PutMapping("/couriers/me/profile")
    @Operation(summary = "Register my vehicle (COURIER)",
            description = "Once only, after accepting the invite. 409 when already registered: from then on only the "
                    + "dispatcher can change it. maxCapacity is ignored; the vehicle type's default applies.")
    public ApiResponse<CourierProfileResponse> registerMyProfile(@Valid @RequestBody CourierVehicleRequest request,
                                                                 HttpServletRequest httpRequest) {
        CallerContext caller = CallerContext.from(httpRequest::getHeader);
        return ApiResponse.success(courierProfileService.registerOwn(caller, request), "Vehicle registered");
    }

    @GetMapping("/couriers/profiles")
    @Operation(summary = "List courier vehicles",
            description = "A DISPATCHER sees their own district (the district parameter is ignored); a SYSTEM_ADMIN "
                    + "sees every district or the one given.")
    public ApiResponse<List<CourierProfileResponse>> listProfiles(
            @RequestParam(required = false) SriLankanDistrict district,
            HttpServletRequest httpRequest) {
        CallerContext caller = CallerContext.from(httpRequest::getHeader);
        return ApiResponse.success(courierProfileService.list(caller, district), "Courier profiles retrieved");
    }

    @PutMapping("/couriers/{courierId}/profile")
    @Operation(summary = "Update a courier's vehicle (DISPATCHER, SYSTEM_ADMIN)",
            description = "A DISPATCHER may change couriers of their own district only. The courier's shift picks up "
                    + "the new vehicle type and capacity straight away.")
    public ApiResponse<CourierProfileResponse> updateProfile(@PathVariable UUID courierId,
                                                             @Valid @RequestBody CourierVehicleRequest request,
                                                             HttpServletRequest httpRequest) {
        CallerContext caller = CallerContext.from(httpRequest::getHeader);
        return ApiResponse.success(courierProfileService.updateByOperations(caller, courierId, request),
                "Courier vehicle updated");
    }

    @GetMapping("/orders/{orderId}/courier")
    @Operation(summary = "The courier handling an order",
            description = "For the order's customer (or merchant), so they can recognise the courier: first name and "
                    + "vehicle only. data is null while no courier is actively assigned.")
    public ApiResponse<OrderCourierResponse> getOrderCourier(@PathVariable UUID orderId,
                                                             HttpServletRequest httpRequest) {
        CallerContext caller = CallerContext.from(httpRequest::getHeader);
        OrderCourierResponse courier = courierProfileService.courierForOrder(caller, orderId);
        return ApiResponse.success(courier, courier == null ? "No courier assigned yet" : "Courier retrieved");
    }
}
