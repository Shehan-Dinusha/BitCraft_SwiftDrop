package com.swiftdrop.pricing.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.swiftdrop.contracts.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pricing-quote")
@Tag(name = "PricingQuote", description = "PricingQuote API operations")
public class PricingQuoteController {
    @GetMapping("/health")
    @Operation(summary = "GET /health")
    public ApiResponse<String> health() {
        return ApiResponse.success("PricingQuoteController is ready");
    }
}
