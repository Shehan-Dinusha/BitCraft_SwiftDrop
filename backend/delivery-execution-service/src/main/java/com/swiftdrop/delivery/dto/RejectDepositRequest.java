package com.swiftdrop.delivery.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Why an admin rejects a deposit.")
public record RejectDepositRequest(
        @NotBlank(message = "Say why")
        @Size(max = 256, message = "Must be at most 256 characters")
        @Schema(description = "Reason", example = "No such slip at the bank")
        String reason
) {
}
