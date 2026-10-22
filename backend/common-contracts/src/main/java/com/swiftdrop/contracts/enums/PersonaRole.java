package com.swiftdrop.contracts.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Platform user persona roles used for RBAC across all services.
 * Mapped from JWT claims via {@link com.swiftdrop.contracts.security.JwtHeaderConstants}.
 */
@Schema(description = "Platform persona used for role-based access control.", example = "CUSTOMER")
public enum PersonaRole {
    CUSTOMER,
    COURIER,
    MERCHANT,
    ADMIN_OPS
}
