package com.swiftdrop.delivery.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.swiftdrop.contracts.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/delivery-execution")
@Tag(name = "DeliveryExecution", description = "DeliveryExecution API operations")
public class DeliveryExecutionController {
    @GetMapping("/health")
    @Operation(summary = "GET /health")
    public ApiResponse<String> health() {
        return ApiResponse.success("DeliveryExecutionController is ready");
    }
}
