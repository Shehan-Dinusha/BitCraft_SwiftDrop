package com.swiftdrop.delivery.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "The dispatcher banked all the cash at their hub.")
public record CreateDepositRequest(
        @NotNull(message = "The amount is required")
        @DecimalMin(value = "0.01", message = "The amount must be more than 0")
        @Schema(description = "The hub cash shown to the dispatcher; refused if it changed meanwhile", example = "25400.00")
        BigDecimal amount,

        @NotBlank(message = "Enter the bank slip number")
        @Size(max = 64, message = "Must be at most 64 characters")
        @Schema(description = "Bank slip number", example = "BOC-884213")
        String reference
) {
}
