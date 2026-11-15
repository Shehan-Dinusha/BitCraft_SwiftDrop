package com.swiftdrop.iam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Identifies an account by username or email address (resend-verification, forgot-password).")
public record IdentifierRequest(
        @NotBlank
        @Size(max = 320)
        @Schema(description = "Username or email address of the account.", example = "customer.one@example.com")
        String identifier
) {
}
