package com.swiftdrop.dispatch.security;

import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.security.CallerContext;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Role checks for dispatch operations, based on the identity headers the API
 * gateway sets after verifying the caller's JWT (X-User-Id, X-User-Role).
 *
 * <p>Missing or malformed identity headers raise {@code UnauthenticatedCallerException}
 * (401); a verified caller without a permitted role raises {@code ForbiddenException}
 * (403). Both are mapped by {@code GlobalExceptionHandler}.</p>
 */
public final class DispatchAccess {

    /** Roles that run the dispatch queue: allocation, suggestions, fleet and workload views. */
    static final UserRole[] OPERATIONS_ROLES = {UserRole.DISPATCHER, UserRole.SYSTEM_ADMIN};

    private DispatchAccess() {
    }

    /** Allows only DISPATCHER and SYSTEM_ADMIN callers. */
    public static CallerContext requireOperations(HttpServletRequest request) {
        CallerContext caller = CallerContext.from(request::getHeader);
        caller.requireRole(OPERATIONS_ROLES);
        return caller;
    }
}
