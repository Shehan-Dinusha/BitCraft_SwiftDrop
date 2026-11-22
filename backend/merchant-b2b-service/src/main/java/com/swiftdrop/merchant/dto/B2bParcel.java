package com.swiftdrop.merchant.dto;

import com.swiftdrop.contracts.parcel.ParcelLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

@Schema(description = "Parcel; supply exactly one of actualWeightKg and weightGrams")
public record B2bParcel(
        @DecimalMin("0.01") @DecimalMax("50") @Schema(description = "Weight in kilograms") BigDecimal actualWeightKg,
        @DecimalMin("10") @DecimalMax("50000") @Schema(description = "Weight in grams") BigDecimal weightGrams,
        @NotNull(message = "Parcel length is required") @DecimalMin("0.1")
        @DecimalMax(value = ParcelLimits.MAX_SIDE_CM, message = ParcelLimits.MAX_SIDE_MESSAGE)
        @Schema(description = "Length in cm", example = "30") BigDecimal lengthCm,
        @NotNull(message = "Parcel width is required") @DecimalMin("0.1")
        @DecimalMax(value = ParcelLimits.MAX_SIDE_CM, message = ParcelLimits.MAX_SIDE_MESSAGE)
        @Schema(description = "Width in cm", example = "20") BigDecimal widthCm,
        @NotNull(message = "Parcel height is required") @DecimalMin("0.1")
        @DecimalMax(value = ParcelLimits.MAX_SIDE_CM, message = ParcelLimits.MAX_SIDE_MESSAGE)
        @Schema(description = "Height in cm", example = "10") BigDecimal heightCm,
        @NotNull @AssertTrue @Schema(description = "Explicit confirmation that no prohibited items are included") Boolean prohibitedItemsDeclared,
        @Size(max = 512) @Pattern(regexp = "[^<>]*") @Schema(description = "Optional contents description") String itemDescription,
        @DecimalMin("0") @Schema(description = "Declared value in LKR") BigDecimal declaredValue,
        // Refused rather than ignored, so a merchant never believes the courier collects their money.
        @DecimalMax(value = "0", message = "SwiftDrop collects no money from the recipient; leave codAmount out")
        @Schema(hidden = true) BigDecimal codAmount
) {}
