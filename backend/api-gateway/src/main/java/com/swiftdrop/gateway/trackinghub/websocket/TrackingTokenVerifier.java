package com.swiftdrop.gateway.trackinghub.websocket;

import com.swiftdrop.contracts.enums.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Verifies the JWT supplied in the STOMP CONNECT frame and authorizes access to
 * private customer topics. Public tracking topics remain anonymous.
 */
@Component
public class TrackingTokenVerifier {

    private static final String TRACK_TOPIC_PREFIX = "/topic/track/";
    private static final String CUSTOMER_TOPIC_PREFIX = "/topic/customer/";
    private static final String BEARER_PREFIX = "Bearer ";

    private final SecretKey signingKey;

    public TrackingTokenVerifier(@Value("${swiftdrop.jwt.secret}") String jwtSecret) {
        this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public StompPrincipal verify(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return null;
        }
        String token = authorizationHeader.startsWith(BEARER_PREFIX)
                ? authorizationHeader.substring(BEARER_PREFIX.length())
                : authorizationHeader;
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            UserRole role = UserRole.fromClaim(claimAsString(claims, "role"));
            return new StompPrincipal(claims.getSubject(), role == null ? null : role.name());
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String claimAsString(Claims claims, String name) {
        Object value = claims.get(name);
        return value == null ? null : value.toString();
    }

    public boolean maySubscribe(StompPrincipal principal, String destination) {
        if (destination == null || destination.isBlank()) {
            return false;
        }
        if (destination.startsWith(TRACK_TOPIC_PREFIX)) {
            return true;
        }
        if (destination.startsWith(CUSTOMER_TOPIC_PREFIX)) {
            if (principal == null || principal.subject() == null) {
                return false;
            }
            String customerId = destination.substring(CUSTOMER_TOPIC_PREFIX.length());
            return UserRole.SYSTEM_ADMIN.name().equals(principal.role())
                    || principal.subject().equalsIgnoreCase(customerId);
        }
        return false;
    }

    public record StompPrincipal(String subject, String role) {
        public boolean isAdmin() {
            return role != null && UserRole.SYSTEM_ADMIN.name().equals(role.toUpperCase(Locale.ROOT));
        }
    }
}
