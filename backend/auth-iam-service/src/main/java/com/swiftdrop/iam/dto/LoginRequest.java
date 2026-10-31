package com.swiftdrop.iam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Login credentials. The username field also accepts an email address.")
public record LoginRequest(
        @NotBlank
        @Size(max = 320)
        @Schema(description = "Username or email address.", example = "customer.one")
        String username,

        @NotBlank
        @Size(max = 72)
        @Schema(description = "Account password.", example = "SwiftDrop123")
        String password
) {
}
