package com.swiftdrop.dispatch.security;

import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.security.CallerContext;
import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

/**
 * Role checks for dispatch operations, based on the identity headers the API
 * gateway sets after verifying the caller's JWT (X-User-Id, X-User-Role).
 *
 * <p>A courier's dispatch identity is their IAM user ID: the JWT subject that the
 * gateway forwards as X-User-Id is the {@code courierId} of their shift record and
 * assignments. A courier therefore owns a resource when its courier ID equals the
 * caller's user ID.</p>
 *
 * <p>Missing or malformed identity headers raise {@code UnauthenticatedCallerException}
 * (401); a verified caller without a permitted role, or a courier acting on another
 * courier's resource, raises {@code ForbiddenException} (403). Both are mapped by
 * {@code GlobalExceptionHandler}.</p>
 */
public final class DispatchAccess {

    /** Roles that run the dispatch queue: allocation, suggestions, fleet and workload views. */
    static final UserRole[] OPERATIONS_ROLES = {UserRole.DISPATCHER, UserRole.SYSTEM_ADMIN};

    /** Roles that may use courier resources at all; couriers are further limited to their own. */
    static final UserRole[] COURIER_RESOURCE_ROLES = {UserRole.COURIER, UserRole.DISPATCHER, UserRole.SYSTEM_ADMIN};

    private DispatchAccess() {
    }

    /** Allows only DISPATCHER and SYSTEM_ADMIN callers. */
    public static CallerContext requireOperations(HttpServletRequest request) {
        CallerContext caller = CallerContext.from(request::getHeader);
        caller.requireRole(OPERATIONS_ROLES);
        return caller;
    }

    /**
     * Allows the courier whose ID is {@code courierId}, or DISPATCHER and SYSTEM_ADMIN
     * for any courier. The role is checked first: CallerContext's self check compares
     * IDs only, so a CUSTOMER or MERCHANT must not pass it with their own user ID.
     */
    public static CallerContext requireCourierSelfOrOperations(HttpServletRequest request, UUID courierId) {
        CallerContext caller = requireCourierResourceRole(request);
        caller.requireSelfOrRole(courierId, OPERATIONS_ROLES);
        return caller;
    }

    /**
     * First half of an assignment lookup: verifies the caller may look up
     * assignments at all, before anything is loaded, so other roles cannot learn
     * which records exist.
     */
    public static CallerContext requireCourierResourceRole(HttpServletRequest request) {
        CallerContext caller = CallerContext.from(request::getHeader);
        caller.requireRole(COURIER_RESOURCE_ROLES);
        return caller;
    }

    /**
     * Second half of an assignment lookup, once the record is loaded: DISPATCHER and
     * SYSTEM_ADMIN see every assignment, including UNASSIGNED ones (no courier); a
     * courier sees only an assignment currently held by them.
     */
    public static void requireAssignmentAccess(CallerContext caller, UUID assignedCourierId) {
        if (isOperations(caller)) {
            return;
        }
        caller.requireSelfOrRole(assignedCourierId);
    }

    public static boolean isOperations(CallerContext caller) {
        return caller.role() == UserRole.DISPATCHER || caller.role() == UserRole.SYSTEM_ADMIN;
    }
}
