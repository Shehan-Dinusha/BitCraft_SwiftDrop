package com.swiftdrop.iam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "The token from a staff invite link (the part after #token=).")
public record InviteTokenRequest(
        @NotBlank
        @Size(max = 64)
        @Schema(description = "Invite token from the emailed link.")
        String token
) {
}
