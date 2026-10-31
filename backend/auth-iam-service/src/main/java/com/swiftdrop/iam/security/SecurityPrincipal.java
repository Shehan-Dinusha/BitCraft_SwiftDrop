package com.swiftdrop.iam.security;

import com.swiftdrop.iam.exception.AuthException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

import java.util.UUID;

public final class SecurityPrincipal {

    private SecurityPrincipal() {
    }

    public static UUID userId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException exception) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Invalid authentication principal");
        }
    }
}
