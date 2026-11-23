package com.swiftdrop.delivery.controller;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.contracts.security.CallerContext;
import com.swiftdrop.delivery.dto.DeliverySessionResponse;
import com.swiftdrop.delivery.dto.LoadParcelsRequest;
import com.swiftdrop.delivery.service.HubDeskService;
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
@RequestMapping("/api/v1/deliveries")
@Tag(name = "HubDesk", description = "Loading parcels at the hub before a run, and handing them in after it")
public class HubDeskController {

    private final HubDeskService hubDeskService;

    public HubDeskController(HubDeskService hubDeskService) {
        this.hubDeskService = hubDeskService;
    }

    @PostMapping("/load")
    @Operation(summary = "Load the chosen parcels at the hub",
            description = "COURIER only. Each parcel must be waiting at the hub for this courier: a delivery goes out "
                    + "for delivery, a return goes out to the sender. All of them, or none (409).")
    public ApiResponse<List<DeliverySessionResponse>> load(@Valid @RequestBody LoadParcelsRequest request,
                                                           HttpServletRequest http) {
        return ApiResponse.success(hubDeskService.load(CallerContext.from(http::getHeader), request).stream()
                .map(DeliverySessionResponse::fromEntity).toList(), "Parcels loaded");
    }

    @PostMapping("/hand-in")
    @Operation(summary = "Hand in the chosen parcels at the courier's hub",
            description = "COURIER only, for parcels they carry. A pickup going through the hub is handed in "
                    + "(AT_ORIGIN_HUB); a failed attempt is held (HELD_AT_HUB); a return the sender could not take "
                    + "goes back (RETURN_AT_HUB); a parcel not delivered today goes back for another run "
                    + "(AT_DESTINATION_HUB), which is not an attempt. All of them, or none (409).")
    public ApiResponse<List<DeliverySessionResponse>> handIn(@Valid @RequestBody LoadParcelsRequest request,
                                                             HttpServletRequest http) {
        return ApiResponse.success(hubDeskService.handIn(CallerContext.from(http::getHeader), request).stream()
                .map(DeliverySessionResponse::fromEntity).toList(), "Parcels handed in");
    }
}
