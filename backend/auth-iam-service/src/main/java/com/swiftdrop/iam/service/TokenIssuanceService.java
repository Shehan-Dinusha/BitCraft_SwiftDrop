package com.swiftdrop.iam.service;

import com.swiftdrop.iam.config.JwtConfig;
import com.swiftdrop.iam.domain.entity.UserAccount;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
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

    public TokenIssuanceService(JwtConfig jwtConfig, StringRedisTemplate redisTemplate) {
        this.jwtConfig = jwtConfig;
        this.redisTemplate = redisTemplate;
        this.signingKey = Keys.hmacShaKeyFor(jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public IssuedAccessToken issueAccessToken(UserAccount user) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusMillis(jwtConfig.getAccessTokenExpirationMs());
        String jwtId = UUID.randomUUID().toString();

        String token = Jwts.builder()
                .issuer(jwtConfig.getIssuer())
                .audience().add(jwtConfig.getAudience()).and()
                .subject(user.getPublicId().toString())
                .id(jwtId)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .claim("role", user.getRole().name())
                .claim("username", user.getUsername())
                .claim("email", user.getEmail())
                .claim("districtId", user.getDistrict() == null ? null : user.getDistrict().name())
                .claim("merchantId", user.getMerchantId() == null ? null : user.getMerchantId().toString())
                .signWith(signingKey)
                .compact();

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
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isBlacklisted(String token, String publicId, String jwtId) {
        try {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(
                    jwtConfig.getBlacklistPrefix() + "token:" + hashToken(token)))) {
                return true;
            }
            if (publicId != null && !publicId.isBlank()
                    && Boolean.TRUE.equals(redisTemplate.hasKey(
                    jwtConfig.getBlacklistPrefix() + "user:" + publicId))) {
                return true;
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

    public void blacklistUser(UUID publicId) {
        try {
            redisTemplate.opsForValue().set(
                    jwtConfig.getBlacklistPrefix() + "user:" + publicId,
                    "1",
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
