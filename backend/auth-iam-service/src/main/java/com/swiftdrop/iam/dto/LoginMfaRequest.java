package com.swiftdrop.iam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Second sign-in step: the challenge from /login plus the emailed code.")
public record LoginMfaRequest(
        @NotBlank @Size(max = 64)
        @Schema(description = "challengeId from the 202 /login response.")
        String challengeId,
        @NotBlank @Pattern(regexp = "^[0-9]{6}$", message = "Code must be 6 digits")
        @Schema(description = "6-digit code from the email.", example = "123456")
        String code
) {
}
