package com.swiftdrop.iam.dto;

import com.swiftdrop.contracts.enums.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Administrator request to change a user's lifecycle status.")
public record UpdateUserStatusRequest(
        @NotNull
        @Schema(description = "New account status.", example = "SUSPENDED")
        UserStatus status
) {
}
