package com.swiftdrop.merchant.security;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.security.CallerContext;
import com.swiftdrop.contracts.security.ForbiddenException;
import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

/**
 * Caller checks for merchant endpoints, based on the identity headers the API gateway sets after
 * verifying the JWT (X-User-Id, X-User-Role, X-Merchant-Id, X-District-Id).
 *
 * <ul>
 *   <li>A merchant user may act only on their own merchant company (the JWT's merchantId).</li>
 *   <li>A DISPATCHER reviews the applications of their own district.</li>
 *   <li>A SYSTEM_ADMIN oversees every district.</li>
 * </ul>
 * Missing headers raise {@code UnauthenticatedCallerException} (401) and a refused caller
 * {@code ForbiddenException} (403), both mapped by {@code GlobalExceptionHandler}.
 */
public final class MerchantAccess {

    private MerchantAccess() {
    }

    public static CallerContext caller(HttpServletRequest request) {
        return CallerContext.from(request::getHeader);
    }

    /** A MERCHANT user acting on their own merchant company. */
    public static CallerContext requireOwnMerchant(HttpServletRequest request, UUID merchantId) {
        CallerContext caller = caller(request);
        caller.requireRole(UserRole.MERCHANT);
        caller.requireMerchant(merchantId);
        return caller;
    }

    /** The merchant's own users, or operations staff (a dispatcher only for their own district). */
    public static CallerContext requireOwnMerchantOrOperations(HttpServletRequest request, UUID merchantId,
                                                               SriLankanDistrict merchantDistrict) {
        CallerContext caller = caller(request);
        switch (caller.role()) {
            case SYSTEM_ADMIN -> { }
            case DISPATCHER -> caller.requireDistrict(merchantDistrict);
            case MERCHANT -> caller.requireMerchant(merchantId);
            default -> throw new ForbiddenException("Caller cannot access this merchant");
        }
        return caller;
    }

    /** A DISPATCHER: reviewing merchant applications is district work. */
    public static CallerContext requireDispatcher(HttpServletRequest request) {
        CallerContext caller = caller(request);
        caller.requireRole(UserRole.DISPATCHER);
        return caller;
    }

    /** DISPATCHER or SYSTEM_ADMIN. */
    public static CallerContext requireOperations(HttpServletRequest request) {
        CallerContext caller = caller(request);
        caller.requireRole(UserRole.DISPATCHER, UserRole.SYSTEM_ADMIN);
        return caller;
    }

    /**
     * Only the dispatcher of the application's district decides on it. A SYSTEM_ADMIN oversees merchants
     * (list, suspend, reactivate) but does not review applications.
     */
    public static void requireReviewer(CallerContext caller, SriLankanDistrict applicationDistrict) {
        caller.requireRole(UserRole.DISPATCHER);
        caller.requireDistrict(applicationDistrict);
    }

    /** The district a reviewer may list: a dispatcher's own, or the admin's choice (null for all). */
    public static SriLankanDistrict reviewDistrict(CallerContext caller, SriLankanDistrict requested) {
        if (caller.role() == UserRole.DISPATCHER) {
            if (caller.districtId() == null) {
                throw new ForbiddenException("Your dispatcher account has no district");
            }
            return caller.districtId();
        }
        return requested;
    }
}
