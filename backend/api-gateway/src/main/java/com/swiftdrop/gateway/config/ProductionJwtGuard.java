package com.swiftdrop.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Fail-fast check for the production ({@code aks}) profile.
 *
 * <p>Why: if {@code JWT_ALGORITHM} went missing, the gateway would otherwise start in HS256 mode
 * and trust any token signed with the shared secret. In production only RS256 tokens verified
 * against the IAM JWKS endpoint are acceptable, so the gateway refuses to start instead.
 */
@Component
@Profile("aks")
public class ProductionJwtGuard {

    public ProductionJwtGuard(
            @Value("${swiftdrop.jwt.algorithm:}") String algorithm,
            @Value("${swiftdrop.jwt.jwks-url:}") String jwksUrl) {
        if (!"RS256".equalsIgnoreCase(algorithm)) {
            throw new IllegalStateException("The aks profile requires JWT_ALGORITHM=RS256, got '" + algorithm + "'");
        }
        if (jwksUrl.isBlank()) {
            throw new IllegalStateException("The aks profile requires JWT_JWKS_URL");
        }
    }
}
