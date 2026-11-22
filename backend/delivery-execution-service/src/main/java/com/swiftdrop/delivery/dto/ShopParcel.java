package com.swiftdrop.delivery.dto;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "One of a shop's parcels the courier collects.")
public record ShopParcel(
        @Schema(description = "Delivery session UUID") UUID deliveryId,
        @Schema(description = "Tracking number", example = "SD-7K4M-9QX2") String trackingNumber,
        @Schema(description = "What is inside", example = "Shirts") String itemDescription,
        @Schema(description = "Weight in kg", example = "1.00") BigDecimal weightKg,
        @Schema(description = "Where it goes") SriLankanDistrict deliveryDistrict,
        @Schema(description = "Goes to another district, so it is handed in at the hub") boolean viaHub,
        @Schema(description = "Delivery fee", example = "350.00") BigDecimal fee
) {
}
