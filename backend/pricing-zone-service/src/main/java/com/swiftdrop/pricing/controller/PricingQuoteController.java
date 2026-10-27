package com.swiftdrop.pricing.controller;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.pricing.domain.entity.PricingZone;
import com.swiftdrop.pricing.domain.repository.PricingZoneRepository;
import com.swiftdrop.pricing.dto.PricingQuoteRequest;
import com.swiftdrop.pricing.dto.PricingQuoteResponse;
import com.swiftdrop.pricing.service.DynamicPricingEngine;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/api/v1/pricing", "/api/v1/pricing-quote"})
@Tag(name = "Pricing", description = "Delivery fee quotation and geofence pricing zones")
@RequiredArgsConstructor
public class PricingQuoteController {

    private final DynamicPricingEngine dynamicPricingEngine;
    private final PricingZoneRepository pricingZoneRepository;

    @GetMapping("/health")
    @Operation(summary = "Check pricing service health")
    public ApiResponse<String> health() {
        return ApiResponse.success("Pricing service is online");
    }

    @PostMapping("/quote")
    @Operation(summary = "Calculate dynamic delivery fee quotation")
    public ApiResponse<PricingQuoteResponse> calculateQuote(@Valid @RequestBody PricingQuoteRequest request) {
        return ApiResponse.success(dynamicPricingEngine.calculateQuote(request), "Quotation calculated successfully");
    }

    @GetMapping("/zones")
    @Operation(summary = "List all active pricing geofence zones")
    public ApiResponse<List<PricingZone>> listZones() {
        return ApiResponse.success(pricingZoneRepository.findAll(), "Pricing zones retrieved successfully");
    }
}
