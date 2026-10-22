package com.swiftdrop.merchant.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.swiftdrop.contracts.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/merchant-order-ingest")
@Tag(name = "MerchantOrderIngest", description = "MerchantOrderIngest API operations")
public class MerchantOrderIngestController {
    @GetMapping("/health")
    @Operation(summary = "GET /health")
    public ApiResponse<String> health() {
        return ApiResponse.success("MerchantOrderIngestController is ready");
    }
}
