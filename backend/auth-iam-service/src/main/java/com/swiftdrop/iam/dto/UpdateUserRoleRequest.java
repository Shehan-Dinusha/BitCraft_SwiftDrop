package com.swiftdrop.iam.dto;

import com.swiftdrop.contracts.enums.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Compatibility request to verify an existing role. Account roles cannot be changed after creation.")
public record UpdateUserRoleRequest(
        @NotNull
        @Schema(description = "Existing role. A different role returns 409.", example = "DISPATCHER")
        UserRole role
) {
}
