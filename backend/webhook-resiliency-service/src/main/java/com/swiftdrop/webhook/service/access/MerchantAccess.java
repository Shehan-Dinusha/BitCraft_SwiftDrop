package com.swiftdrop.webhook.service.access;

import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.security.CallerContext;
import com.swiftdrop.contracts.security.ForbiddenException;

import java.util.UUID;

/**
 * Merchant tenancy rules shared by the webhook services.
 *
 * <p>The caller comes from gateway-injected headers ({@link CallerContext}). The merchant identity is the JWT
 * {@code merchantId} claim, never the user id: the two are different values.</p>
 *
 * <ul>
 *   <li>{@code MERCHANT}: only its own merchant's data; must carry a merchant identity.</li>
 *   <li>{@code SYSTEM_ADMIN}: any merchant.</li>
 *   <li>{@code DISPATCHER}: read-only dead-letter metadata across merchants; granted per operation by the
 *       caller of these helpers, never through {@link #canAccess}.</li>
 *   <li>Every other role: no access.</li>
 * </ul>
 *
 * <p>Single-resource checks deliberately return a boolean so callers answer "not found" for another merchant's
 * resource, exactly as for a missing one.</p>
 */
public final class MerchantAccess {

    private MerchantAccess() {
    }

    /** Rejects (403) every role except the given ones, and a merchant caller without a merchant identity. */
    public static void requireRole(CallerContext caller, UserRole... allowedRoles) {
        caller.requireRole(allowedRoles);
        if (caller.role() == UserRole.MERCHANT && caller.merchantId() == null) {
            throw new ForbiddenException("Caller has no merchant identity");
        }
    }

    /** Whether the caller may see or change a resource owned by {@code ownerMerchantId}. */
    public static boolean canAccess(CallerContext caller, UUID ownerMerchantId) {
        if (ownerMerchantId == null) {
            return false;
        }
        return switch (caller.role()) {
            case SYSTEM_ADMIN -> true;
            case MERCHANT -> ownerMerchantId.equals(caller.merchantId());
            default -> false;
        };
    }

    /**
     * Merchant filter for a list or batch operation.
     *
     * @param requestedMerchantId optional {@code merchantId} filter from the request
     * @param crossMerchantRoles  roles allowed to see every merchant (they may also filter)
     * @return the merchant to filter by, or {@code null} meaning all merchants (cross-merchant roles only)
     */
    public static UUID listScope(CallerContext caller, UUID requestedMerchantId, UserRole... crossMerchantRoles) {
        for (UserRole role : crossMerchantRoles) {
            if (caller.role() == role) {
                return requestedMerchantId;
            }
        }
        if (caller.role() != UserRole.MERCHANT) {
            throw new ForbiddenException("Caller role is not permitted");
        }
        if (requestedMerchantId != null && !requestedMerchantId.equals(caller.merchantId())) {
            throw new ForbiddenException("Caller cannot access this merchant's resources");
        }
        return caller.merchantId();
    }

    /**
     * Owner of a new resource: a merchant always gets its own identity (a different explicit value is refused);
     * a system administrator must name the merchant explicitly.
     */
    public static UUID ownerForCreate(CallerContext caller, UUID requestedMerchantId) {
        if (caller.role() == UserRole.SYSTEM_ADMIN) {
            if (requestedMerchantId == null) {
                throw new IllegalArgumentException("merchantId is required when an administrator creates an endpoint");
            }
            return requestedMerchantId;
        }
        if (requestedMerchantId != null && !requestedMerchantId.equals(caller.merchantId())) {
            throw new ForbiddenException("Caller cannot create endpoints for another merchant");
        }
        return caller.merchantId();
    }
}
