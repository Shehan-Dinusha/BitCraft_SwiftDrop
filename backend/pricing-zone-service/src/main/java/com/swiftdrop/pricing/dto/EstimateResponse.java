package com.swiftdrop.pricing.dto;

import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
@Schema(description = "Calculated delivery fee estimate")
public record EstimateResponse(

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

        @Schema(description = "Actual package weight in kg", example = "2.50")
        BigDecimal actualWeightKg,

        @Schema(description = "Volumetric weight in kg (L*W*H / 5000)", example = "0.60")
        BigDecimal volumetricWeightKg,

        @Schema(description = "Chargeable billable weight in kg", example = "2.50")
        BigDecimal chargeableWeightKg,

        @Schema(description = "Delivery speed that was priced", example = "EXPRESS")
        OrderSpeed speed,

        @Schema(description = "Base starting fee in LKR", example = "550.00")
        BigDecimal baseFee,

        @Schema(description = "Additional weight fee in LKR", example = "240.00")
        BigDecimal weightFee,

        @Schema(description = "Speed surcharge in LKR", example = "395.00")
        BigDecimal speedSurcharge,

        @Schema(description = "Total estimated delivery fee in LKR", example = "1185.00")
        BigDecimal estimatedFee,

        @Schema(description = "Currency code", example = "LKR")
        String currency,

        @Schema(description = "Locked pricing rule version ID for order anchoring", example = "PRICING-2026-V1")
        String pricingRuleVersionId,

        @Schema(description = "Pricing strategy used (STANDARD or EXPRESS)", example = "EXPRESS")
        String strategy
) {

    /** Builds an estimate from the pricing engine result. */
    public static EstimateResponse from(PricingQuoteResponse quote) {
        return EstimateResponse.builder()
                .pickupDistrict(quote.pickupDistrict())
                .deliveryDistrict(quote.deliveryDistrict())
                .pickupZone(quote.pickupZone())
                .deliveryZone(quote.deliveryZone())
                .intraDistrict(quote.intraDistrict())
                .actualWeightKg(quote.actualWeightKg())
                .volumetricWeightKg(quote.volumetricWeightKg())
                .chargeableWeightKg(quote.chargeableWeightKg())
                .speed(quote.speed())
                .baseFee(quote.baseFee())
                .weightFee(quote.weightFee())
                .speedSurcharge(quote.speedSurcharge())
                .estimatedFee(quote.estimatedFee())
                .currency(quote.currency())
                .pricingRuleVersionId(quote.pricingRuleVersionId())
                .strategy(quote.speed() == OrderSpeed.STANDARD ? "STANDARD" : "EXPRESS")
                .build();
    }
}
