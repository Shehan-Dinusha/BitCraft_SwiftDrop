package com.swiftdrop.pricing.dto;

import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
@Schema(description = "Request payload to calculate a delivery fee estimate")
public record EstimateRequest(

        @NotNull(message = "Pickup district is mandatory")
        @Schema(description = "Pickup administrative district", example = "COLOMBO")
        SriLankanDistrict pickupDistrict,

        @NotNull(message = "Delivery district is mandatory")
        @Schema(description = "Delivery administrative district", example = "KANDY")
        SriLankanDistrict deliveryDistrict,

        @NotNull(message = "Actual weight is mandatory")
        @DecimalMin(value = "0.01", message = "Actual weight must be at least 0.01 kg")
        @Schema(description = "Actual package weight in kilograms", example = "2.50")
        BigDecimal actualWeightKg,

        @DecimalMin(value = "0.1", message = "Length must be at least 0.1 cm")
        @Schema(description = "Length in centimetres", example = "20.0")
        BigDecimal lengthCm,

        @DecimalMin(value = "0.1", message = "Width must be at least 0.1 cm")
        @Schema(description = "Width in centimetres", example = "15.0")
        BigDecimal widthCm,

        @DecimalMin(value = "0.1", message = "Height must be at least 0.1 cm")
        @Schema(description = "Height in centimetres", example = "10.0")
        BigDecimal heightCm,

        @Schema(description = "Delivery speed (STANDARD, EXPRESS or SAME_DAY)", example = "EXPRESS")
        OrderSpeed speed
) {

    public EstimateRequest {
        if (speed == null) {
            speed = OrderSpeed.STANDARD;
        }
    }

    /** Converts this request to the request type used by the pricing engine. */
    public PricingQuoteRequest toQuoteRequest() {
        return PricingQuoteRequest.builder()
                .pickupDistrict(pickupDistrict)
                .deliveryDistrict(deliveryDistrict)
                .actualWeightKg(actualWeightKg)
                .lengthCm(lengthCm)
                .widthCm(widthCm)
                .heightCm(heightCm)
                .speed(speed)
                .build();
    }
}
