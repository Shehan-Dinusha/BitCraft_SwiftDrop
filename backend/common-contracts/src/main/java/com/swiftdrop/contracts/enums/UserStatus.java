package com.swiftdrop.contracts.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Lifecycle status of an authenticated platform account.
 * Only {@link #ACTIVE} accounts may authenticate or receive tokens.
 */
@Schema(description = "Lifecycle status of a platform account.", example = "ACTIVE")
public enum UserStatus {
    ACTIVE,
    SUSPENDED,
    DEACTIVATED
}
