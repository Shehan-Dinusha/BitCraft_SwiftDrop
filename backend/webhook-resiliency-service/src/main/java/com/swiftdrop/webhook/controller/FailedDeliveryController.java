package com.swiftdrop.webhook.controller;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.contracts.security.CallerContext;
import com.swiftdrop.webhook.dto.FailedDeliveryResponse;
import com.swiftdrop.webhook.service.FailedDeliveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/webhooks/deliveries/failed")
@Tag(name = "FailedDeliveries", description = "Webhook deliveries that used up their attempts, and replaying them")
@RequiredArgsConstructor
public class FailedDeliveryController {

    private final FailedDeliveryService service;

    @GetMapping
    @Operation(summary = "List failed deliveries, newest first (up to 200)",
            description = "A MERCHANT sees its own; a SYSTEM_ADMIN every merchant's. Other roles: 403.")
    public ApiResponse<List<FailedDeliveryResponse>> list(CallerContext caller) {
        return ApiResponse.success(service.list(caller), "Failed deliveries retrieved");
    }

    @PostMapping("/{deliveryId}/replay")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Queue a failed delivery to be sent again",
            description = "Sent in the background with the same event id and a new delivery id, on the normal retry "
                    + "schedule. Safe to repeat: one that is already waiting, running or delivered is returned unchanged. "
                    + "Another merchant's delivery returns 404, like an unknown ID.")
    public ApiResponse<FailedDeliveryResponse> replay(
            @PathVariable @Parameter(description = "Failed delivery UUID") UUID deliveryId, CallerContext caller) {
        return ApiResponse.success(service.replay(deliveryId, caller), "Replay queued");
    }

    @PostMapping("/replay")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Queue every failed delivery that has not been replayed",
            description = "Returns how many were queued.")
    public ApiResponse<Integer> replayAll(CallerContext caller) {
        return ApiResponse.success(service.replayAll(caller), "Replays queued");
    }
}
