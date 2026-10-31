package com.swiftdrop.iam.dto;

import com.swiftdrop.contracts.enums.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Administrator request to change a user's role.")
public record UpdateUserRoleRequest(
        @NotNull
        @Schema(description = "New role.", example = "DISPATCHER")
        UserRole role
) {
}
