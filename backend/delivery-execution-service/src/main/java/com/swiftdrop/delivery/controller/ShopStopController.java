package com.swiftdrop.delivery.controller;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.contracts.security.CallerContext;
import com.swiftdrop.delivery.dto.CollectShopRequest;
import com.swiftdrop.delivery.dto.CollectShopResponse;
import com.swiftdrop.delivery.dto.ShopStopResponse;
import com.swiftdrop.delivery.service.ShopStopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/deliveries/shops")
@Tag(name = "ShopStop", description = "Collecting a shop's parcels in one visit, with one payment")
public class ShopStopController {

    private final ShopStopService shopStopService;

    public ShopStopController(ShopStopService shopStopService) {
        this.shopStopService = shopStopService;
    }

    @GetMapping("/{merchantId}")
    @Operation(summary = "The shop's parcels this courier collects, with the address, contact and fees")
    public ResponseEntity<ApiResponse<ShopStopResponse>> stop(@PathVariable UUID merchantId, HttpServletRequest http) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(
                shopStopService.stop(CallerContext.from(http::getHeader), merchantId), "Shop stop retrieved"));
    }

    @PostMapping("/{merchantId}/collect")
    @Operation(summary = "Pick up the chosen parcels and confirm one payment for them",
            description = "All of them, or none. The amount must be exactly their fees; parcels left out stay for later.")
    public ApiResponse<CollectShopResponse> collect(@PathVariable UUID merchantId,
                                                    @Valid @RequestBody CollectShopRequest request,
                                                    HttpServletRequest http) {
        return ApiResponse.success(
                shopStopService.collect(CallerContext.from(http::getHeader), merchantId, request), "Parcels collected");
    }
}
