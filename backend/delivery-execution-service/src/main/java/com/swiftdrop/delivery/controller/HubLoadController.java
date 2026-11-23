package com.swiftdrop.delivery.controller;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.contracts.security.CallerContext;
import com.swiftdrop.delivery.dto.DeliverySessionResponse;
import com.swiftdrop.delivery.dto.LoadParcelsRequest;
import com.swiftdrop.delivery.service.HubLoadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/deliveries/load")
@Tag(name = "HubLoad", description = "Loading the parcels waiting at the hub before the courier's run")
public class HubLoadController {

    private final HubLoadService hubLoadService;

    public HubLoadController(HubLoadService hubLoadService) {
        this.hubLoadService = hubLoadService;
    }

    @PostMapping
    @Operation(summary = "Load the chosen parcels at the hub",
            description = "COURIER only. Each parcel must be waiting at the hub for this courier: a delivery goes out "
                    + "for delivery, a return goes out to the sender. All of them, or none (409).")
    public ApiResponse<List<DeliverySessionResponse>> load(@Valid @RequestBody LoadParcelsRequest request,
                                                           HttpServletRequest http) {
        return ApiResponse.success(hubLoadService.load(CallerContext.from(http::getHeader), request).stream()
                .map(DeliverySessionResponse::fromEntity).toList(), "Parcels loaded");
    }
}
