package com.swiftdrop.pricing.dto;

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
@Schema(description = "Calculated delivery pricing quote response")
public class PricingQuoteResponse {

    @Schema(description = "Pickup district", example = "COLOMBO")
    private SriLankanDistrict pickupDistrict;

    @Schema(description = "Delivery district", example = "KANDY")
    private SriLankanDistrict deliveryDistrict;

    @Schema(description = "Resolved pickup zone code", example = "ZONE_WESTERN")
    private String pickupZone;

    @Schema(description = "Resolved delivery zone code", example = "ZONE_CENTRAL")
    private String deliveryZone;

    @Schema(description = "Whether pickup and delivery are in the same district", example = "false")
    private boolean intraDistrict;

    @Schema(description = "Actual package weight in kg", example = "1.50")
    private BigDecimal actualWeightKg;

    @Schema(description = "Volumetric weight in kg (L*W*H / 5000)", example = "1.00")
    private BigDecimal volumetricWeightKg;

    @Schema(description = "Chargeable billable weight in kg", example = "1.50")
    private BigDecimal chargeableWeightKg;

    @Schema(description = "Selected delivery service speed", example = "STANDARD")
    private OrderSpeed speed;

    @Schema(description = "Base starting fee (first 1 kg included)", example = "550.00")
    private BigDecimal baseFee;

    @Schema(description = "Additional weight surcharge", example = "60.00")
    private BigDecimal weightFee;

    @Schema(description = "Speed surcharge or multiplier applied", example = "0.00")
    private BigDecimal speedSurcharge;

    @Schema(description = "Total estimated delivery fee in LKR", example = "610.00")
    private BigDecimal estimatedFee;

    @Schema(description = "Currency code", example = "LKR")
    @Builder.Default
    private String currency = "LKR";

    @Schema(description = "Locked pricing rule version ID for order anchoring", example = "PRICING-2026-V1")
    private String pricingRuleVersionId;
}
