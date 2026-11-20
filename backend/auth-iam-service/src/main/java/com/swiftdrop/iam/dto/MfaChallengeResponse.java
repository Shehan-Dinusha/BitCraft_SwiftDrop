package com.swiftdrop.iam.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Returned with 202 by /login when the account needs a second factor. "
        + "A 6-digit code was emailed; finish with POST /api/v1/auth/login/mfa.")
public record MfaChallengeResponse(
        @Schema(description = "Always true; distinguishes this body from a TokenResponse.", example = "true")
        boolean mfaRequired,
        @Schema(description = "Opaque, single-use id of this sign-in attempt.")
        String challengeId,
        @Schema(description = "Minutes until the emailed code expires.", example = "10")
        int expiresInMinutes
) {
}
