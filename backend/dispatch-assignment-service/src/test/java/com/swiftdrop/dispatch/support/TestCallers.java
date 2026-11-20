package com.swiftdrop.dispatch.support;

import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.security.JwtHeaderConstants;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

/**
 * Adds the identity headers the API gateway sets after verifying a JWT, so
 * MockMvc requests reach dispatch the same way gateway traffic does.
 */
public final class TestCallers {

    private TestCallers() {
    }

    public static RequestPostProcessor dispatcher() {
        return as(UserRole.DISPATCHER);
    }

    /** The courier signed in as {@code courierId}: their IAM user ID is their courier ID. */
    public static RequestPostProcessor courier(UUID courierId) {
        return as(courierId, UserRole.COURIER);
    }

    public static RequestPostProcessor as(UserRole role) {
        return as(UUID.randomUUID(), role);
    }

    public static RequestPostProcessor as(UUID userId, UserRole role) {
        return withIdentity(userId.toString(), role.name());
    }

    /** Raw header values, for tests that send malformed identities. */
    public static RequestPostProcessor withIdentity(String userId, String role) {
        return request -> {
            request.addHeader(JwtHeaderConstants.X_USER_ID, userId);
            request.addHeader(JwtHeaderConstants.X_USER_ROLE, role);
            return request;
        };
    }
}
