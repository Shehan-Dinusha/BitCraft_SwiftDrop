package com.swiftdrop.pricing.dto;

import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload to calculate a delivery pricing quote")
public class PricingQuoteRequest {

    @NotNull(message = "Pickup district is mandatory")
    @Schema(description = "Pickup administrative district", example = "COLOMBO")
    private SriLankanDistrict pickupDistrict;

    @NotNull(message = "Delivery district is mandatory")
    @Schema(description = "Delivery administrative district", example = "KANDY")
    private SriLankanDistrict deliveryDistrict;

    @NotNull(message = "Actual weight is mandatory")
    @DecimalMin(value = "0.01", message = "Actual weight must be at least 0.01 kg")
    @Schema(description = "Actual package weight in kilograms", example = "1.50")
    private BigDecimal actualWeightKg;

    @DecimalMin(value = "0.1", message = "Length must be at least 0.1 cm")
    @Schema(description = "Length in centimetres", example = "25.0")
    private BigDecimal lengthCm;

    @DecimalMin(value = "0.1", message = "Width must be at least 0.1 cm")
    @Schema(description = "Width in centimetres", example = "20.0")
    private BigDecimal widthCm;

    @DecimalMin(value = "0.1", message = "Height must be at least 0.1 cm")
    @Schema(description = "Height in centimetres", example = "10.0")
    private BigDecimal heightCm;

    @Schema(description = "Delivery speed service level", example = "STANDARD")
    @Builder.Default
    private OrderSpeed speed = OrderSpeed.STANDARD;
}
