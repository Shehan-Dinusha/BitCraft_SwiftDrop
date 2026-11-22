package com.swiftdrop.delivery.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "The dispatcher's count of a handover.")
public record CountHandoverRequest(
        @NotNull(message = "Enter the amount you counted")
        @DecimalMin(value = "0.00", message = "The amount cannot be negative")
        @Digits(integer = 10, fraction = 2, message = "Use at most 2 decimals")
        @Schema(description = "Amount counted", example = "1340.00")
        BigDecimal countedAmount,

        @Size(max = 256, message = "Must be at most 256 characters")
        @Schema(description = "Why it differs; required when it does")
        String note
) {
}
