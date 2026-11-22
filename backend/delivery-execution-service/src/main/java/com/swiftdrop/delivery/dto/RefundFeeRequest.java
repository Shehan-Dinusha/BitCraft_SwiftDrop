package com.swiftdrop.delivery.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Give a sender their delivery fee back because SwiftDrop lost or damaged the parcel.")
public record RefundFeeRequest(
        @NotBlank(message = "Enter the tracking number")
        @Size(max = 32, message = "Must be at most 32 characters")
        @Schema(description = "Tracking number", example = "SD-7K4M-9QX2")
        String trackingNumber,

        @NotBlank(message = "Say why")
        @Size(max = 256, message = "Must be at most 256 characters")
        @Schema(description = "What happened", example = "Crushed in the van")
        String reason
) {
}
