package com.swiftdrop.delivery.security;

import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.security.CallerContext;
import com.swiftdrop.contracts.security.ForbiddenException;
import com.swiftdrop.contracts.security.UnauthenticatedCallerException;
import com.swiftdrop.delivery.domain.entity.DeliverySession;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Access policy enforcing horizontal authorisation for delivery resources.
 * Only the courier assigned to the delivery session, or privileged roles
 * (DISPATCHER, SYSTEM_ADMIN), are permitted access.
 */
@Component
public class DeliveryAccessPolicy {

    /**
     * Asserts that the caller is permitted to access the given delivery session.
     *
     * @param session the delivery session entity
     * @param caller  the verified caller identity from gateway headers
     * @throws ForbiddenException             if the caller is not permitted access
     * @throws UnauthenticatedCallerException if caller context is missing
     */
    public static void assertCanAccess(DeliverySession session, CallerContext caller) {
        if (session == null) {
            throw new ForbiddenException("Delivery session is null or not found");
        }
        assertCanAccess(session.getCourierId(), caller);
    }

    /**
     * Asserts that the caller is permitted to access resources belonging to assignedCourierId.
     *
     * @param assignedCourierId the UUID of the courier assigned to the resource
     * @param caller            the verified caller identity from gateway headers
     * @throws ForbiddenException             if the caller is not permitted access
     * @throws UnauthenticatedCallerException if caller context is missing
     */
    public static void assertCanAccess(UUID assignedCourierId, CallerContext caller) {
        if (caller == null) {
            throw new UnauthenticatedCallerException("Caller context is required");
        }

        UserRole role = caller.role();

        // Privileged operational roles bypass horizontal ownership checks
        if (role == UserRole.DISPATCHER || role == UserRole.SYSTEM_ADMIN) {
            return;
        }

        // Courier role: must strictly match the assigned courier's ID
        if (role == UserRole.COURIER) {
            if (assignedCourierId == null || !assignedCourierId.equals(caller.userId())) {
                throw new ForbiddenException("Courier does not own this delivery resource");
            }
            return;
        }

        // Other non-courier roles (e.g. CUSTOMER, MERCHANT) are forbidden from courier delivery operations
        throw new ForbiddenException("Caller role " + role + " is not authorized for courier delivery operations");
    }
}

