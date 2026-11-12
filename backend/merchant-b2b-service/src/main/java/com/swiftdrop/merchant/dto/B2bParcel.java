package com.swiftdrop.merchant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

@Schema(description = "Parcel; supply exactly one of actualWeightKg and weightGrams")
public record B2bParcel(
        @DecimalMin("0.01") @DecimalMax("50") @Schema(description = "Weight in kilograms") BigDecimal actualWeightKg,
        @DecimalMin("10") @DecimalMax("50000") @Schema(description = "Weight in grams") BigDecimal weightGrams,
        @NotNull @AssertTrue @Schema(description = "Explicit confirmation that no prohibited items are included") Boolean prohibitedItemsDeclared,
        @Size(max = 512) @Pattern(regexp = "[^<>]*") @Schema(description = "Optional contents description") String itemDescription,
        @DecimalMin("0") @Schema(description = "Declared value in LKR") BigDecimal declaredValue,
        @DecimalMin("0") @Schema(description = "Cash on delivery amount in LKR") BigDecimal codAmount
) {}
