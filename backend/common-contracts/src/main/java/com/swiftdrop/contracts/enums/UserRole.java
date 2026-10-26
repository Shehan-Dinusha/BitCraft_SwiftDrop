package com.swiftdrop.contracts.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Platform user roles used for role-based access control across all services.
 * The API Gateway maps the JWT {@code role} claim into the
 * {@link com.swiftdrop.contracts.security.JwtHeaderConstants#X_USER_ROLE} header.
 */
@Schema(description = "Platform role used for role-based access control.", example = "CUSTOMER")
public enum UserRole {
    CUSTOMER,
    MERCHANT,
    COURIER,
    DISPATCHER,
    SYSTEM_ADMIN;

    /**
     * Normalises legacy role claims (for example {@code ADMIN_OPS}) to the
     * canonical SRS role set.
     */
    public static UserRole fromClaim(String claim) {
        if (claim == null || claim.isBlank()) {
            return null;
        }
        return switch (claim.trim().toUpperCase()) {
            case "ADMIN", "ADMIN_OPS", "SYSTEM_ADMIN" -> SYSTEM_ADMIN;
            case "CUSTOMER" -> CUSTOMER;
            case "MERCHANT" -> MERCHANT;
            case "COURIER", "DRIVER" -> COURIER;
            case "DISPATCHER", "DISPATCH" -> DISPATCHER;
            default -> null;
        };
    }
}
