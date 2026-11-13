package com.swiftdrop.pricing.controller;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.pricing.dto.TariffRuleRequest;
import com.swiftdrop.pricing.dto.TariffRuleResponse;
import com.swiftdrop.pricing.service.TariffAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/pricing/tariffs")
@Tag(name = "Tariff Management", description = "View and publish default tariff versions")
@RequiredArgsConstructor
public class TariffAdminController {

    private final TariffAdminService tariffAdminService;

    @GetMapping
    @Operation(summary = "List all tariff rule versions, newest first")
    public ApiResponse<List<TariffRuleResponse>> listRules() {
        return ApiResponse.success(tariffAdminService.listRules(), "Tariff rules retrieved successfully");
    }

    @GetMapping("/active")
    @Operation(summary = "Get the active default tariff rule")
    public ApiResponse<TariffRuleResponse> getActiveRule() {
        return ApiResponse.success(tariffAdminService.getActiveRule(), "Active tariff rule retrieved successfully");
    }

    @PutMapping("/default")
    @Operation(summary = "Publish a new default tariff version and deactivate the previous one")
    public ApiResponse<TariffRuleResponse> publishNewVersion(@Valid @RequestBody TariffRuleRequest request) {
        return ApiResponse.success(tariffAdminService.publishNewVersion(request), "Tariff version published successfully");
    }
}
