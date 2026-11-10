package com.swiftdrop.iam.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed binding for {@code swiftdrop.oauth2.google.*} in {@code application.yml}.
 * All fields are optional — if {@code clientId} or {@code clientSecret} is blank,
 * the Google OAuth endpoints return 503 and the service continues to work for
 * password-based login and RS256/JWKS without OAuth.
 */
@ConfigurationProperties(prefix = "swiftdrop.oauth2.google")
public class GoogleOAuthConfig {

    /** Google OAuth 2.0 client ID from Google Cloud Console. */
    private String clientId = "";

    /** Google OAuth 2.0 client secret from Google Cloud Console. */
    private String clientSecret = "";

    /**
     * Redirect URI registered in Google Cloud Console.
     * Must match exactly (scheme, host, port, path).
     */
    private String redirectUri = "http://localhost:8080/api/v1/auth/oauth2/google/callback";

    /** TTL in seconds for the CSRF state token stored in Redis. Default: 300 (5 minutes). */
    private int stateTtlSeconds = 300;

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }

    public String getRedirectUri() { return redirectUri; }
    public void setRedirectUri(String redirectUri) { this.redirectUri = redirectUri; }

    public int getStateTtlSeconds() { return stateTtlSeconds; }
    public void setStateTtlSeconds(int stateTtlSeconds) { this.stateTtlSeconds = stateTtlSeconds; }

    /** Returns {@code true} when both client-id and client-secret are configured. */
    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }
}

