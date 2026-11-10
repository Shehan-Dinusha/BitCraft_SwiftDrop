package com.swiftdrop.contracts.security;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.enums.UserRole;

import java.util.UUID;
import java.util.function.Function;

/**
 * Immutable identity from gateway-verified headers, not a JWT verifier.
 * Only use at a boundary that excludes untrusted, directly supplied identity headers.
 * Operation-specific guards must still be applied to the loaded business record.
 */
public record CallerContext(UUID userId, UserRole role, UUID merchantId, SriLankanDistrict districtId) {

    public CallerContext {
        if (userId == null || role == null) {
            throw new UnauthenticatedCallerException("Verified caller user ID and role are required");
        }
    }

    /**
     * Accepts e.g. servlet request::getHeader or reactive headers::getFirst without
     * introducing a servlet or Spring dependency into this shared module.
     * Optional scope headers may be absent, but a supplied malformed value is rejected.
     */
    public static CallerContext from(Function<String, String> headerLookup) {
        if (headerLookup == null) {
            throw new UnauthenticatedCallerException("Verified caller headers are required");
        }
        UUID userId = parseUuid(required(headerLookup, JwtHeaderConstants.X_USER_ID), JwtHeaderConstants.X_USER_ID);
        UserRole role = parseEnum(required(headerLookup, JwtHeaderConstants.X_USER_ROLE),
                UserRole.class, JwtHeaderConstants.X_USER_ROLE);
        String merchant = optional(headerLookup, JwtHeaderConstants.X_MERCHANT_ID);
        String district = optional(headerLookup, JwtHeaderConstants.X_DISTRICT_ID);
        return new CallerContext(userId, role,
                merchant == null ? null : parseUuid(merchant, JwtHeaderConstants.X_MERCHANT_ID),
                district == null ? null : parseEnum(district, SriLankanDistrict.class, JwtHeaderConstants.X_DISTRICT_ID));
    }

    public void requireRole(UserRole... allowedRoles) {
        if (!hasRole(allowedRoles)) {
            throw new ForbiddenException("Caller role is not permitted");
        }
    }

    /** Scope check; use requireRole separately when the operation also restricts roles. */
    public void requireSelfOrRole(UUID ownerUserId, UserRole... privilegedRoles) {
        if (ownerUserId == null || (!userId.equals(ownerUserId) && !hasRole(privilegedRoles))) {
            throw new ForbiddenException("Caller cannot access this user's resource");
        }
    }

    /** An ordinary merchant must have both MERCHANT role and matching business identity. */
    public void requireMerchant(UUID ownerMerchantId, UserRole... privilegedRoles) {
        if (ownerMerchantId == null || (!hasRole(privilegedRoles)
                && (role != UserRole.MERCHANT || !ownerMerchantId.equals(merchantId)))) {
            throw new ForbiddenException("Caller cannot access this merchant's resource");
        }
    }

    /** Scope check; a matching district alone does not grant an operational role. */
    public void requireDistrict(SriLankanDistrict resourceDistrict, UserRole... privilegedRoles) {
        if (resourceDistrict == null || (!hasRole(privilegedRoles) && resourceDistrict != districtId)) {
            throw new ForbiddenException("Caller cannot access this district's resource");
        }
    }

    private boolean hasRole(UserRole... allowedRoles) {
        if (allowedRoles != null) {
            for (UserRole allowedRole : allowedRoles) {
                if (role == allowedRole) return true;
            }
        }
        return false;
    }

    private static String required(Function<String, String> headers, String name) {
        String value = optional(headers, name);
        if (value == null) {
            throw new UnauthenticatedCallerException("Missing " + name + " header");
        }
        return value;
    }

    private static String optional(Function<String, String> headers, String name) {
        String value = headers.apply(name);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static UUID parseUuid(String value, String name) {
        try {
            UUID parsed = UUID.fromString(value);
            // UUID.fromString also accepts shortened groups such as 1-1-1-1-1.
            if (!parsed.toString().equalsIgnoreCase(value)) {
                throw new IllegalArgumentException();
            }
            return parsed;
        } catch (IllegalArgumentException exception) {
            throw new UnauthenticatedCallerException("Invalid " + name + " header");
        }
    }

    private static <E extends Enum<E>> E parseEnum(String value, Class<E> type, String name) {
        try {
            // Do not promote aliases such as the frontend persona ADMIN_OPS to SYSTEM_ADMIN.
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw new UnauthenticatedCallerException("Invalid " + name + " header");
        }
    }
}
