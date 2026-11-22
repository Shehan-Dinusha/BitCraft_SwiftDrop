package com.swiftdrop.delivery.controller;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.delivery.dto.InternalCourierCashResponse;
import com.swiftdrop.delivery.service.CourierCashService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Service-to-service only: the gateway never routes /internal/**. */
@RestController
@RequestMapping("/internal/v1/cash")
@Tag(name = "Internal Cash", description = "Service-to-service endpoints. Trusted network only.")
public class InternalCashController {

    private final CourierCashService courierCashService;

    public InternalCashController(CourierCashService courierCashService) {
        this.courierCashService = courierCashService;
    }

    @GetMapping("/couriers/{courierId}/in-hand")
    @Operation(summary = "Cash a courier has not handed over yet",
            description = "Called by the dispatch service before a courier ends their shift.")
    public ApiResponse<InternalCourierCashResponse> inHand(@PathVariable UUID courierId) {
        return ApiResponse.success(
                new InternalCourierCashResponse(courierCashService.inHand(courierId), "LKR"), "Cash retrieved");
    }
}
