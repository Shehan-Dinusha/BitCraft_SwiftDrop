package com.swiftdrop.order.dto;

import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Pre-confirmation delivery fee estimate response (FR-C2)")
public class FeeEstimateResponse {

    @Schema(description = "Pickup district", example = "COLOMBO")
    private SriLankanDistrict pickupDistrict;

    @Schema(description = "Delivery district", example = "KANDY")
    private SriLankanDistrict deliveryDistrict;

    @Schema(description = "Whether intra-district or inter-district", example = "false")
    private boolean intraDistrict;

    @Schema(description = "Actual weight in kg", example = "1.50")
    private BigDecimal actualWeightKg;

    @Schema(description = "Volumetric weight in kg", example = "1.00")
    private BigDecimal volumetricWeightKg;

    @Schema(description = "Chargeable billable weight in kg", example = "1.50")
    private BigDecimal chargeableWeightKg;

    @Schema(description = "Selected speed", example = "STANDARD")
    private OrderSpeed speed;

    @Schema(description = "Base rate in LKR (first 1 kg included)", example = "550.00")
    private BigDecimal baseFee;

    @Schema(description = "Weight tier surcharge in LKR", example = "60.00")
    private BigDecimal weightFee;

    @Schema(description = "Speed surcharge in LKR", example = "0.00")
    private BigDecimal speedSurcharge;

    @Schema(description = "Total estimated fee in LKR", example = "610.00")
    private BigDecimal estimatedFee;

    @Schema(description = "Currency", example = "LKR")
    @Builder.Default
    private String currency = "LKR";

    @Schema(description = "Locked pricing rule version ID", example = "PRICING-2026-V1")
    private String pricingRuleVersionId;
}
