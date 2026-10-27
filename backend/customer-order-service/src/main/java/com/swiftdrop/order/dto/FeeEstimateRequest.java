package com.swiftdrop.order.dto;

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
@Schema(description = "Request payload to calculate pre-confirmation fee estimation (FR-C2)")
public class FeeEstimateRequest {

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
    @Schema(description = "Package length in cm", example = "25.0")
    private BigDecimal lengthCm;

    @DecimalMin(value = "0.1", message = "Width must be at least 0.1 cm")
    @Schema(description = "Package width in cm", example = "20.0")
    private BigDecimal widthCm;

    @DecimalMin(value = "0.1", message = "Height must be at least 0.1 cm")
    @Schema(description = "Package height in cm", example = "10.0")
    private BigDecimal heightCm;

    @Schema(description = "Delivery service level speed", example = "STANDARD")
    @Builder.Default
    private OrderSpeed speed = OrderSpeed.STANDARD;
}
