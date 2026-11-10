package com.swiftdrop.iam.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.iam.config.JwtConfig;
import com.swiftdrop.iam.config.JwtKeyProvider;
import com.swiftdrop.iam.domain.entity.UserAccount;
import com.swiftdrop.iam.support.RsaTestKeys;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.env.MockEnvironment;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class RsaTokenIssuanceServiceTest {
    private JwtConfig config;
    private JwtKeyProvider keys;
    private TokenIssuanceService service;
    private UserAccount user;

    @BeforeEach
    void setUp() {
        config = RsaTestKeys.config(RsaTestKeys.PRIMARY);
        config.setSecret(""); // RSA must not require or use a shared secret.
        keys = new JwtKeyProvider(config, new MockEnvironment());
        service = new TokenIssuanceService(config, mock(StringRedisTemplate.class), keys);
        user = new UserAccount();
        user.setPublicId(UUID.randomUUID());
        user.setUsername("rsa-test");
        user.setRole(UserRole.CUSTOMER);
    }

    @Test
    void signsRs256WithStableKidAndVerifiesWithPublicKeyOnly() throws Exception {
        var token = service.issueAccessToken(user);
        var header = new ObjectMapper().readTree(Base64.getUrlDecoder().decode(token.token().split("\\.")[0]));
        assertThat(header.path("alg").asText()).isEqualTo("RS256");
        assertThat(header.path("kid").asText()).isEqualTo(keys.keyId());
        assertThat(Jwts.parser().verifyWith(RsaTestKeys.PRIMARY.getPublic()).build()
                .parseSignedClaims(token.token()).getPayload().getSubject()).isEqualTo(user.getPublicId().toString());
        assertThat(service.parse(token.token()).getId()).isEqualTo(token.jwtId());
    }

    @Test
    void rejectsTamperedSignature() {
        String token = service.issueAccessToken(user).token();
        String[] parts = token.split("\\.");
        byte[] signature = Base64.getUrlDecoder().decode(parts[2]);
        signature[0] ^= 1;
        String tampered = parts[0] + "." + parts[1] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        assertThatThrownBy(() -> service.parse(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsUnknownOrMissingKidEvenWithValidSignature() {
        String unknown = builder().header().keyId("unknown").and().signWith(keys.signingKey(), Jwts.SIG.RS256).compact();
        String missing = builder().signWith(keys.signingKey(), Jwts.SIG.RS256).compact();
        assertThatThrownBy(() -> service.parse(unknown)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> service.parse(missing)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsAnotherRsaKeyWithCopiedKid() {
        String token = builder().header().keyId(keys.keyId()).and().signWith(RsaTestKeys.OTHER.getPrivate(), Jwts.SIG.RS256).compact();
        assertThatThrownBy(() -> service.parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsWrongIssuerAndAudience() {
        String issuer = builder().issuer("other").header().keyId(keys.keyId()).and().signWith(keys.signingKey(), Jwts.SIG.RS256).compact();
        String audience = Jwts.builder().issuer(config.getIssuer()).audience().add("other").and()
                .expiration(Date.from(Instant.now().plusSeconds(60))).header().keyId(keys.keyId()).and()
                .signWith(keys.signingKey(), Jwts.SIG.RS256).compact();
        assertThatThrownBy(() -> service.parse(issuer)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> service.parse(audience)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsExpiredAndNotYetValidTokens() {
        String expired = builder().expiration(Date.from(Instant.now().minusSeconds(60))).header().keyId(keys.keyId()).and()
                .signWith(keys.signingKey(), Jwts.SIG.RS256).compact();
        String future = builder().notBefore(Date.from(Instant.now().plusSeconds(60))).header().keyId(keys.keyId()).and()
                .signWith(keys.signingKey(), Jwts.SIG.RS256).compact();
        assertThatThrownBy(() -> service.parse(expired)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> service.parse(future)).isInstanceOf(JwtException.class);
    }

    @Test
    void refusesAlgorithmChangesChosenByTheToken() {
        String rs512 = builder().header().keyId(keys.keyId()).and().signWith(keys.signingKey(), Jwts.SIG.RS512).compact();
        String hs256 = builder().header().keyId(keys.keyId()).and().signWith(Keys.hmacShaKeyFor(
                "test-shared-secret-long-enough-for-hs256".getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256).compact();
        String unsigned = builder().compact();
        assertThatThrownBy(() -> service.parse(rs512)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> service.parse(hs256)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> service.parse(unsigned)).isInstanceOf(JwtException.class);
    }

    @Test
    void hs256RemainsExplicitAndRejectsRsaTokens() throws Exception {
        String rsa = service.issueAccessToken(user).token();
        var legacy = new JwtConfig();
        var hs = new TokenIssuanceService(legacy, mock(StringRedisTemplate.class), new JwtKeyProvider(legacy, new MockEnvironment()));
        String token = hs.issueAccessToken(user).token();
        var header = new ObjectMapper().readTree(Base64.getUrlDecoder().decode(token.split("\\.")[0]));
        assertThat(header.path("alg").asText()).isEqualTo("HS256");
        assertThat(header.has("kid")).isFalse();
        assertThat(hs.parse(token).getSubject()).isEqualTo(user.getPublicId().toString());
        assertThatThrownBy(() -> hs.parse(rsa)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsMissingLifecycleClaimsAndNonCanonicalIdentity() {
        var incomplete = Jwts.builder().issuer(config.getIssuer()).audience().add(config.getAudience()).and()
                .subject(user.getPublicId().toString()).claim("role", "CUSTOMER")
                .header().keyId(keys.keyId()).and().signWith(keys.signingKey(), Jwts.SIG.RS256).compact();
        assertThatThrownBy(() -> service.parse(incomplete)).isInstanceOf(JwtException.class);
        for (String subject : new String[]{"1-1-1-1-1", "invalid"}) {
            String invalid = builder().subject(subject).header().keyId(keys.keyId()).and()
                    .signWith(keys.signingKey(), Jwts.SIG.RS256).compact();
            assertThatThrownBy(() -> service.parse(invalid)).isInstanceOf(JwtException.class);
        }
        String alias = builder().claim("role", "ADMIN_OPS").header().keyId(keys.keyId()).and()
                .signWith(keys.signingKey(), Jwts.SIG.RS256).compact();
        assertThatThrownBy(() -> service.parse(alias)).isInstanceOf(JwtException.class);
    }

    private io.jsonwebtoken.JwtBuilder builder() {
        return Jwts.builder().issuer(config.getIssuer()).audience().add(config.getAudience()).and()
                .subject(user.getPublicId().toString()).id(UUID.randomUUID().toString()).issuedAt(new Date())
                .claim("role", "CUSTOMER").expiration(Date.from(Instant.now().plusSeconds(120)));
    }
}
