package com.swiftdrop.iam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Confirms that the person asking to erase an account really is its owner. */
@Schema(description = "Request to erase the signed-in customer's own account.")
public record DeleteAccountRequest(
        @NotBlank
        @Size(max = 72)
        @Schema(description = "The account's current password, as confirmation.")
        String currentPassword
) {
}
