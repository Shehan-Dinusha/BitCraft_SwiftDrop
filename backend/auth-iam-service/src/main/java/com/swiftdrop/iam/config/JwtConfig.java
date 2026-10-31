package com.swiftdrop.iam.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "swiftdrop.jwt")
public class JwtConfig {

    private String secret = "swiftdrop-local-dev-secret-key-must-be-at-least-256-bits-long!!";
    private String issuer = "swiftdrop-iam";
    private String audience = "swiftdrop";
    private long accessTokenExpirationMs = 3_600_000L;
    private long refreshTokenExpirationDays = 30L;
    private String blacklistPrefix = "jwt:blacklist:";

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public long getAccessTokenExpirationMs() {
        return accessTokenExpirationMs;
    }

    public void setAccessTokenExpirationMs(long accessTokenExpirationMs) {
        this.accessTokenExpirationMs = accessTokenExpirationMs;
    }

    public long getRefreshTokenExpirationDays() {
        return refreshTokenExpirationDays;
    }

    public void setRefreshTokenExpirationDays(long refreshTokenExpirationDays) {
        this.refreshTokenExpirationDays = refreshTokenExpirationDays;
    }

    public String getBlacklistPrefix() {
        return blacklistPrefix;
    }

    public void setBlacklistPrefix(String blacklistPrefix) {
        this.blacklistPrefix = blacklistPrefix;
    }
}
