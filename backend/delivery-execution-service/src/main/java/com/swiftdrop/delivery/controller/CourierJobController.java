package com.swiftdrop.delivery.controller;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.contracts.security.CallerContext;
import com.swiftdrop.delivery.dto.CourierJobResponse;
import com.swiftdrop.delivery.service.CourierJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/deliveries")
@Tag(name = "CourierJob", description = "Addresses, contacts and cash for the courier carrying a parcel")
public class CourierJobController {

    private final CourierJobService courierJobService;

    public CourierJobController(CourierJobService courierJobService) {
        this.courierJobService = courierJobService;
    }

    @GetMapping("/{deliveryId}/job")
    @Operation(summary = "Get the addresses, contacts and cash for a delivery",
            description = "For the assigned courier, a DISPATCHER of either district, or a SYSTEM_ADMIN. Shows only "
                    + "what the current leg needs: the sender and the fee until pickup, and the recipient for the "
                    + "courier who delivers.")
    public ResponseEntity<ApiResponse<CourierJobResponse>> getJob(
            @PathVariable UUID deliveryId,
            HttpServletRequest request) {
        CallerContext caller = CallerContext.from(request::getHeader);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(courierJobService.getJob(deliveryId, caller), "Delivery job retrieved"));
    }
}
