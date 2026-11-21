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

    /** The district every test dispatcher works in unless a test says otherwise. */
    public static final String DISPATCHER_DISTRICT = "COLOMBO";

    /** A dispatcher of {@link #DISPATCHER_DISTRICT}, as the gateway sends it (X-District-Id). */
    public static RequestPostProcessor dispatcher() {
        return dispatcherOf(DISPATCHER_DISTRICT);
    }

    public static RequestPostProcessor dispatcherOf(String district) {
        return request -> {
            withIdentity(UUID.randomUUID().toString(), UserRole.DISPATCHER.name()).postProcessRequest(request);
            request.addHeader(JwtHeaderConstants.X_DISTRICT_ID, district);
            return request;
        };
    }

    /** The courier signed in as {@code courierId}: their IAM user ID is their courier ID. */
    public static RequestPostProcessor courier(UUID courierId) {
        return as(courierId, UserRole.COURIER);
    }

    public static RequestPostProcessor as(UserRole role) {
        return as(UUID.randomUUID(), role);
    }

    public static RequestPostProcessor as(UUID userId, UserRole role) {
        if (role == UserRole.DISPATCHER) {
            // A dispatcher account always has a district.
            return request -> {
                withIdentity(userId.toString(), role.name()).postProcessRequest(request);
                request.addHeader(JwtHeaderConstants.X_DISTRICT_ID, DISPATCHER_DISTRICT);
                return request;
            };
        }
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
