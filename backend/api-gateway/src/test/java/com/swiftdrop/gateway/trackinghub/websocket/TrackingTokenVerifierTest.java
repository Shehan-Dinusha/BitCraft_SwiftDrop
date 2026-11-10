package com.swiftdrop.gateway.trackinghub.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.gateway.security.JwksKeyStore;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TrackingTokenVerifierTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    private final TrackingTokenVerifier verifier = new TrackingTokenVerifier(
            "HS256", SECRET, "swiftdrop-iam", "swiftdrop",
            mock(JwksKeyStore.class), new ObjectMapper());

    @Test
    void rejectsMissingAndMalformedTokens() {
        assertThat(verifier.verify(null)).isNull();
        assertThat(verifier.verify("")).isNull();
        assertThat(verifier.verify("not-a-jwt")).isNull();
    }

    @Test
    void resolvesCustomerPrincipalFromBearerToken() {
        String token = token("customer-123", "CUSTOMER", "swiftdrop-iam", "swiftdrop");

        TrackingTokenVerifier.StompPrincipal principal = verifier.verify("Bearer " + token);

        assertThat(principal).isNotNull();
        assertThat(principal.subject()).isEqualTo("customer-123");
        assertThat(principal.role()).isEqualTo("CUSTOMER");
    }

    @Test
    void rejectsTokenWithWrongIssuerOrAudience() {
        assertThat(verifier.verify("Bearer "
                + token("customer-123", "CUSTOMER", "evil-issuer", "swiftdrop"))).isNull();
        assertThat(verifier.verify("Bearer "
                + token("customer-123", "CUSTOMER", "swiftdrop-iam", "other-audience"))).isNull();
    }

    @Test
    void allowsPublicTrackTopicsWithoutAuthentication() {
        assertThat(verifier.maySubscribe(null, "/topic/track/SD-1")).isTrue();
    }

    @Test
    void restrictsCustomerTopicsToOwningCustomerOrAdmin() {
        TrackingTokenVerifier.StompPrincipal customer =
                new TrackingTokenVerifier.StompPrincipal("customer-123", "CUSTOMER");
        TrackingTokenVerifier.StompPrincipal admin =
                new TrackingTokenVerifier.StompPrincipal("admin-1", "SYSTEM_ADMIN");

        assertThat(verifier.maySubscribe(customer, "/topic/customer/customer-123")).isTrue();
        assertThat(verifier.maySubscribe(customer, "/topic/customer/customer-999")).isFalse();
        assertThat(verifier.maySubscribe(null, "/topic/customer/customer-123")).isFalse();
        assertThat(verifier.maySubscribe(admin, "/topic/customer/customer-999")).isTrue();
        assertThat(verifier.maySubscribe(customer, "/topic/unknown")).isFalse();
    }

    private String token(String subject, String role, String issuer, String audience) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .issuer(issuer)
                .audience().add(audience).and()
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
