package com.swiftdrop.pricing.dto;

import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
@Schema(description = "Calculated delivery pricing quote response")
public record PricingQuoteResponse(

        @Schema(description = "Pickup district", example = "COLOMBO")
        SriLankanDistrict pickupDistrict,

        @Schema(description = "Delivery district", example = "KANDY")
        SriLankanDistrict deliveryDistrict,

        @Schema(description = "Resolved pickup zone code", example = "ZONE_WESTERN")
        String pickupZone,

        @Schema(description = "Resolved delivery zone code", example = "ZONE_CENTRAL")
        String deliveryZone,

        @Schema(description = "Whether pickup and delivery are in the same district", example = "false")
        boolean intraDistrict,

        @Schema(description = "Actual package weight in kg", example = "1.50")
        BigDecimal actualWeightKg,

        @Schema(description = "Volumetric weight in kg (L*W*H / 5000)", example = "1.00")
        BigDecimal volumetricWeightKg,

        @Schema(description = "Chargeable billable weight in kg", example = "1.50")
        BigDecimal chargeableWeightKg,

        @Schema(description = "Selected delivery service speed", example = "STANDARD")
        OrderSpeed speed,

        @Schema(description = "Base starting fee (first 1 kg included)", example = "550.00")
        BigDecimal baseFee,

        @Schema(description = "Additional weight surcharge", example = "60.00")
        BigDecimal weightFee,

        @Schema(description = "Speed surcharge or multiplier applied", example = "0.00")
        BigDecimal speedSurcharge,

        @Schema(description = "Total estimated delivery fee in LKR", example = "610.00")
        BigDecimal estimatedFee,

        @Schema(description = "Currency code", example = "LKR")
        String currency,

        @Schema(description = "Locked pricing rule version ID for order anchoring", example = "PRICING-2026-V1")
        String pricingRuleVersionId
) {

    public PricingQuoteResponse {
        if (currency == null) {
            currency = "LKR";
        }
    }
}