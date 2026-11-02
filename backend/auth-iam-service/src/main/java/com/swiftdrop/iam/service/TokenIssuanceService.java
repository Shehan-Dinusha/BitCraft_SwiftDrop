package com.swiftdrop.iam.service;

import com.swiftdrop.contracts.security.JwtSessionRevocation;
import com.swiftdrop.iam.config.JwtConfig;
import com.swiftdrop.iam.config.JwtKeyProvider;
import com.swiftdrop.iam.domain.entity.UserAccount;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.MalformedJwtException;
import com.swiftdrop.contracts.enums.UserRole;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Service
public class TokenIssuanceService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int REFRESH_TOKEN_BYTES = 32;

    private final JwtConfig jwtConfig;
    private final StringRedisTemplate redisTemplate;
    private final SecretKey signingKey;
    private final JwtKeyProvider keys;

    public TokenIssuanceService(JwtConfig jwtConfig, StringRedisTemplate redisTemplate, JwtKeyProvider keys) {
        this.jwtConfig = jwtConfig;
        this.redisTemplate = redisTemplate;
        this.keys = keys;
        this.signingKey = keys.isRsaEnabled() ? null
                : Keys.hmacShaKeyFor(jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public IssuedAccessToken issueAccessToken(UserAccount user) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusMillis(jwtConfig.getAccessTokenExpirationMs());
        String jwtId = UUID.randomUUID().toString();

        var builder = Jwts.builder()
                .issuer(jwtConfig.getIssuer())
                .audience().add(jwtConfig.getAudience()).and()
                .subject(user.getPublicId().toString())
                .id(jwtId)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .claim(JwtSessionRevocation.ISSUED_AT_CLAIM, issuedAt.toString())
                .claim("role", user.getRole().name())
                .claim("username", user.getUsername())
                .claim("email", user.getEmail())
                .claim("districtId", user.getDistrict() == null ? null : user.getDistrict().name())
                .claim("merchantId", user.getMerchantId() == null ? null : user.getMerchantId().toString());
        String token = keys.isRsaEnabled()
                ? builder.header().keyId(keys.keyId()).and().signWith(keys.signingKey(), Jwts.SIG.RS256).compact()
                : builder.signWith(signingKey, Jwts.SIG.HS256).compact();

        return new IssuedAccessToken(
                token,
                jwtId,
                expiresAt,
                Math.max(1L, Duration.between(issuedAt, expiresAt).toSeconds())
        );
    }

    public String generateRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public Claims parse(String token) {
        var parser = Jwts.parser().requireIssuer(jwtConfig.getIssuer()).requireAudience(jwtConfig.getAudience());
        var allowed = parser.sig();
        String algorithm = keys.isRsaEnabled() ? "RS256" : "HS256";
        // JJWT 0.12.6 rejects an empty registry, so retain the selected algorithm while removing others.
        for (var candidate : Jwts.SIG.get().values()) {
            if (!algorithm.equals(candidate.getId())) allowed.remove(candidate);
        }
        allowed.and();
        if (keys.isRsaEnabled()) {
            parser.verifyWith(keys.verificationKey());
        } else {
            parser.verifyWith(signingKey);
        }
        var signed = parser.build().parseSignedClaims(token);
        if (keys.isRsaEnabled() && !keys.keyId().equals(signed.getHeader().getKeyId())) {
            throw new UnsupportedJwtException("Unknown signing key identifier");
        }
        Claims claims = signed.getPayload();
        if (claims.getExpiration() == null || claims.getIssuedAt() == null
                || claims.getId() == null || claims.getId().isBlank()) {
            throw new MalformedJwtException("Required access-token lifecycle claims are missing");
        }
        try {
            if (!UUID.fromString(claims.getSubject()).toString().equalsIgnoreCase(claims.getSubject())) {
                throw new IllegalArgumentException();
            }
            UserRole.valueOf(claims.get("role", String.class));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new MalformedJwtException("Required access-token identity claims are invalid");
        }
        return claims;
    }

    public boolean isBlacklisted(String token, Claims claims) {
        String publicId = claims.getSubject();
        String jwtId = claims.getId();
        try {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(
                    jwtConfig.getBlacklistPrefix() + "token:" + hashToken(token)))) {
                return true;
            }
            if (publicId != null && !publicId.isBlank()) {
                String cutoff = redisTemplate.opsForValue().get(jwtConfig.getBlacklistPrefix() + "user:" + publicId);
                if (JwtSessionRevocation.isRevoked(cutoff,
                        claims.get(JwtSessionRevocation.ISSUED_AT_CLAIM, String.class),
                        claims.getIssuedAt() == null ? null : claims.getIssuedAt().toInstant())) {
                    return true;
                }
            }
            return jwtId != null && !jwtId.isBlank()
                    && Boolean.TRUE.equals(redisTemplate.hasKey(
                    jwtConfig.getBlacklistPrefix() + "jti:" + jwtId));
        } catch (RuntimeException exception) {
            log.warn("Redis blacklist check unavailable: {}", exception.getMessage());
            return false;
        }
    }

    public void blacklistAccessToken(String token, String publicId, String jwtId, Instant expiresAt) {
        long ttlSeconds = Duration.between(Instant.now(), expiresAt).toSeconds();
        if (ttlSeconds <= 0) {
            return;
        }
        Duration ttl = Duration.ofSeconds(ttlSeconds);
        try {
            redisTemplate.opsForValue().set(
                    jwtConfig.getBlacklistPrefix() + "token:" + hashToken(token), "1", ttl);
            if (jwtId != null && !jwtId.isBlank()) {
                redisTemplate.opsForValue().set(
                        jwtConfig.getBlacklistPrefix() + "jti:" + jwtId, "1", ttl);
            }
        } catch (RuntimeException exception) {
            log.warn("Unable to blacklist access token: {}", exception.getMessage());
        }
    }

    /** Revoke existing access tokens while permitting tokens from later logins. */
    public void blacklistUser(UUID publicId) {
        try {
            redisTemplate.opsForValue().set(
                    jwtConfig.getBlacklistPrefix() + "user:" + publicId,
                    Instant.now().toString(),
                    Duration.ofMillis(Math.max(1_000L, jwtConfig.getAccessTokenExpirationMs()))
            );
        } catch (RuntimeException exception) {
            log.warn("Unable to blacklist user sessions: {}", exception.getMessage());
        }
    }

    public String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    public long accessTokenExpirationSeconds() {
        return Math.max(1L, jwtConfig.getAccessTokenExpirationMs() / 1_000L);
    }

    public Instant refreshTokenExpiry() {
        return Instant.now().plus(Duration.ofDays(jwtConfig.getRefreshTokenExpirationDays()));
    }

    public record IssuedAccessToken(String token, String jwtId, Instant expiresAt, long expiresInSeconds) {
    }
}
