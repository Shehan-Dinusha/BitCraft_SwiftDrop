package com.swiftdrop.iam.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Whether a username can be used for a new account.")
public record UsernameAvailabilityResponse(
        @Schema(description = "The username that was checked, trimmed.", example = "nimal.perera")
        String username,
        @Schema(description = "True when the username is valid and no account (verified or pending) uses it.")
        boolean available,
        @Schema(description = "Why it is not available: TAKEN or INVALID. Null when available.",
                allowableValues = {"TAKEN", "INVALID"}, nullable = true)
        String reason
) {
    public static final String TAKEN = "TAKEN";
    public static final String INVALID = "INVALID";
}
