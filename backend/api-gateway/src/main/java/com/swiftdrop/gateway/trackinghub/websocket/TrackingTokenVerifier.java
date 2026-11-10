package com.swiftdrop.gateway.trackinghub.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.gateway.security.JwksKeyStore;
import com.swiftdrop.gateway.security.JwsHeader;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParserBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPublicKey;
import java.util.Locale;

/**
 * Verifies the JWT supplied in the STOMP CONNECT frame and authorizes access to
 * private customer topics. Public tracking topics remain anonymous.
 *
 * <p>HS256 uses the shared secret. RS256 resolves the public key by the token's
 * {@code kid}; an unknown key id triggers a throttled background JWKS refresh
 * and rejects the CONNECT so the client reconnects once the key is available.</p>
 */
@Component
public class TrackingTokenVerifier {

    private static final String TRACK_TOPIC_PREFIX = "/topic/track/";
    private static final String CUSTOMER_TOPIC_PREFIX = "/topic/customer/";
    private static final String BEARER_PREFIX = "Bearer ";

    private final SecretKey signingKey;
    private final boolean rs256;
    private final String issuer;
    private final String audience;
    private final JwksKeyStore jwksKeyStore;
    private final ObjectMapper objectMapper;

    public TrackingTokenVerifier(
            @Value("${swiftdrop.jwt.algorithm:HS256}") String algorithm,
            @Value("${swiftdrop.jwt.secret}") String jwtSecret,
            @Value("${swiftdrop.jwt.issuer:swiftdrop-iam}") String issuer,
            @Value("${swiftdrop.jwt.audience:swiftdrop}") String audience,
            JwksKeyStore jwksKeyStore,
            ObjectMapper objectMapper) {
        if (!"HS256".equalsIgnoreCase(algorithm) && !"RS256".equalsIgnoreCase(algorithm)) {
            throw new IllegalArgumentException("JWT_ALGORITHM must be HS256 or RS256");
        }
        this.rs256 = "RS256".equalsIgnoreCase(algorithm);
        this.signingKey = this.rs256 ? null
                : Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.audience = audience;
        this.jwksKeyStore = jwksKeyStore;
        this.objectMapper = objectMapper;
    }

    public StompPrincipal verify(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return null;
        }
        String token = authorizationHeader.startsWith(BEARER_PREFIX)
                ? authorizationHeader.substring(BEARER_PREFIX.length())
                : authorizationHeader;
        try {
            Claims claims = parse(token);
            if (claims == null) {
                return null;
            }
            UserRole role = UserRole.fromClaim(claimAsString(claims, "role"));
            return new StompPrincipal(claims.getSubject(), role == null ? null : role.name());
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private Claims parse(String token) {
        if (!rs256) {
            return baseParser().verifyWith(signingKey).build()
                    .parseSignedClaims(token).getPayload();
        }
        JwsHeader header = JwsHeader.decode(token, objectMapper);
        if (!"RS256".equals(header.algorithm())
                || header.keyId() == null || header.keyId().isBlank()) {
            return null;
        }
        RSAPublicKey key = jwksKeyStore.cached(header.keyId());
        if (key == null) {
            jwksKeyStore.refreshAsync();
            return null;
        }
        return baseParser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
    }

    private JwtParserBuilder baseParser() {
        return Jwts.parser()
                .requireIssuer(issuer)
                .requireAudience(audience);
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
