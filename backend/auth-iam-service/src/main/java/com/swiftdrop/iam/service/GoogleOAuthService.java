package com.swiftdrop.iam.service;

import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.enums.UserStatus;
import com.swiftdrop.iam.config.GoogleOAuthConfig;
import com.swiftdrop.iam.domain.entity.UserAccount;
import com.swiftdrop.iam.domain.repository.UserAccountRepository;
import com.swiftdrop.iam.dto.TokenResponse;
import com.swiftdrop.iam.exception.AuthException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Implements the Google OAuth 2.0 Authorization Code flow for SwiftDrop.
 *
 * <p>Flow summary:
 * <ol>
 *   <li>{@link #buildAuthorizeRedirectUri()} – create a random state, store it in Redis
 *       with a short TTL, and return the Google consent-screen URI.</li>
 *   <li>{@link #handleCallback(String, String, String)} – validate state, exchange the
 *       authorization code for a Google ID token + access token, fetch the Google
 *       user-info profile, upsert a {@code CUSTOMER} {@link UserAccount}, and issue
 *       a one-time exchange code stored in Redis.</li>
 *   <li>{@link #exchangeCode(String)} – swap the one-time code for a {@link TokenResponse}
 *       (access token + refresh token).</li>
 * </ol>
 *
 * <p>No passwords are set on OAuth-sourced accounts; password login is blocked for them
 * via a randomly generated placeholder hash that cannot be reproduced.
 */
@Slf4j
@Service
public class GoogleOAuthService {

    private static final String GOOGLE_AUTH_ENDPOINT =
            "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String GOOGLE_TOKEN_ENDPOINT =
            "https://oauth2.googleapis.com/token";
    private static final String GOOGLE_USERINFO_ENDPOINT =
            "https://www.googleapis.com/oauth2/v3/userinfo";

    /** Redis key prefix for CSRF state tokens (5-minute TTL). */
    private static final String STATE_PREFIX = "oauth2:google:state:";
    /** Redis key prefix for one-time code-exchange tokens (2-minute TTL). */
    private static final String CODE_PREFIX = "oauth2:google:code:";
    /** TTL for the one-time exchange code. */
    private static final Duration CODE_TTL = Duration.ofMinutes(2);
    /** Placeholder used for the password hash of OAuth-only accounts. */
    private static final String OAUTH_PLACEHOLDER_PREFIX = "OAUTH_NO_PASSWORD:";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final GoogleOAuthConfig oauthConfig;
    private final StringRedisTemplate redis;
    private final UserAccountRepository userAccountRepository;
    private final TokenIssuanceService tokenIssuanceService;
    private final RefreshTokenService refreshTokenService;
    private final RestTemplate restTemplate;

    public GoogleOAuthService(GoogleOAuthConfig oauthConfig,
                              StringRedisTemplate redis,
                              UserAccountRepository userAccountRepository,
                              TokenIssuanceService tokenIssuanceService,
                              RefreshTokenService refreshTokenService,
                              RestTemplate restTemplate) {
        this.oauthConfig = oauthConfig;
        this.redis = redis;
        this.userAccountRepository = userAccountRepository;
        this.tokenIssuanceService = tokenIssuanceService;
        this.refreshTokenService = refreshTokenService;
        this.restTemplate = restTemplate;
    }

    // ─── Stage 12: Authorize ────────────────────────────────────────────────────

    /**
     * Builds and returns the Google consent-screen URI.
     * A random CSRF state token is generated, stored in Redis, and embedded in the URI.
     *
     * @return the URI to redirect the browser to.
     * @throws AuthException (503) if Google OAuth is not configured.
     */
    public URI buildAuthorizeRedirectUri() {
        requireConfigured();

        // Generate a 24-byte random state token (URL-safe Base64, no padding = 32 chars).
        byte[] stateBytes = new byte[24];
        SECURE_RANDOM.nextBytes(stateBytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(stateBytes);

        // Store in Redis with TTL so the callback can validate it.
        redis.opsForValue().set(STATE_PREFIX + state, "1",
                Duration.ofSeconds(oauthConfig.getStateTtlSeconds()));

        String uri = GOOGLE_AUTH_ENDPOINT
                + "?client_id=" + encode(oauthConfig.getClientId())
                + "&redirect_uri=" + encode(oauthConfig.getRedirectUri())
                + "&response_type=code"
                + "&scope=" + encode("openid email profile")
                + "&state=" + encode(state)
                + "&access_type=offline"
                + "&prompt=select_account";

        return URI.create(uri);
    }

    // ─── Stage 13: Callback + user upsert ──────────────────────────────────────

    /**
     * Handles the Google callback: validates the state, exchanges the code, upserts the
     * user, and returns a one-time exchange code the frontend can swap for tokens.
     *
     * @param code    the authorization code from Google.
     * @param state   the CSRF state token from Google.
     * @param userAgent the User-Agent header of the original browser request.
     * @return a one-time exchange code (opaque string, 2-minute TTL).
     * @throws AuthException on CSRF violation, Google API error, or inactive account.
     */
    @Transactional
    public String handleCallback(String code, String state, String userAgent) {
        requireConfigured();
        validateState(state);

        // Exchange authorization code for Google tokens.
        Map<String, Object> googleTokens = exchangeCodeWithGoogle(code);
        String googleAccessToken = (String) googleTokens.get("access_token");

        // Fetch user profile from Google.
        GoogleUserInfo userInfo = fetchGoogleUserInfo(googleAccessToken);

        // Upsert the user account.
        UserAccount user = upsertGoogleUser(userInfo);

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AuthException(HttpStatus.FORBIDDEN,
                    "Your account is not active. Contact support.");
        }

        // Update last login timestamp.
        user.setLastLoginAt(Instant.now());
        userAccountRepository.save(user);

        // Issue a one-time exchange code and store it in Redis.
        byte[] codeBytes = new byte[32];
        SECURE_RANDOM.nextBytes(codeBytes);
        String exchangeCode = Base64.getUrlEncoder().withoutPadding().encodeToString(codeBytes);

        // The exchange code maps to the user's public ID.
        redis.opsForValue().set(CODE_PREFIX + exchangeCode,
                user.getPublicId().toString(), CODE_TTL);

        return exchangeCode;
    }

    // ─── Stage 13: Code exchange ────────────────────────────────────────────────

    /**
     * Swaps the one-time exchange code (issued in {@link #handleCallback}) for a full
     * {@link TokenResponse} containing a SwiftDrop access token and refresh token.
     *
     * @param exchangeCode the one-time code.
     * @param ipAddress    the caller's IP (for refresh-token audit).
     * @param userAgent    the caller's User-Agent (for refresh-token audit).
     * @return a {@link TokenResponse} identical in structure to the password-login response.
     * @throws AuthException (400) if the code is missing, expired, or already used.
     */
    @Transactional
    public TokenResponse exchangeCode(String exchangeCode, String ipAddress, String userAgent) {
        if (exchangeCode == null || exchangeCode.isBlank()) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "Exchange code is required");
        }

        String redisKey = CODE_PREFIX + exchangeCode;
        String userIdStr = redis.opsForValue().get(redisKey);
        if (userIdStr == null) {
            throw new AuthException(HttpStatus.BAD_REQUEST,
                    "Exchange code is invalid or has expired");
        }

        // One-time use: delete immediately.
        redis.delete(redisKey);

        UUID publicId;
        try {
            publicId = UUID.fromString(userIdStr);
        } catch (IllegalArgumentException ex) {
            throw new AuthException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Corrupted exchange code payload");
        }

        UserAccount user = userAccountRepository.findByPublicId(publicId)
                .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "User not found"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AuthException(HttpStatus.FORBIDDEN, "Account is not active");
        }

        TokenIssuanceService.IssuedAccessToken accessToken =
                tokenIssuanceService.issueAccessToken(user);
        RefreshTokenService.IssuedRefreshToken refreshToken =
                refreshTokenService.create(user, userAgent, ipAddress);

        return AuthMapper.toTokenResponse(user, accessToken, refreshToken.rawToken());
    }

    // ─── Helpers ────────────────────────────────────────────────────────────────

    private void requireConfigured() {
        if (!oauthConfig.isConfigured()) {
            throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Google OAuth is not configured on this instance");
        }
    }

    private void validateState(String state) {
        if (state == null || state.isBlank()) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "Missing OAuth state parameter");
        }
        String key = STATE_PREFIX + state;
        Boolean exists = redis.hasKey(key);
        if (!Boolean.TRUE.equals(exists)) {
            throw new AuthException(HttpStatus.BAD_REQUEST,
                    "Invalid or expired OAuth state. Please start the login flow again.");
        }
        // State is single-use: delete immediately after validation.
        redis.delete(key);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> exchangeCodeWithGoogle(String code) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("code", code);
        body.add("client_id", oauthConfig.getClientId());
        body.add("client_secret", oauthConfig.getClientSecret());
        body.add("redirect_uri", oauthConfig.getRedirectUri());
        body.add("grant_type", "authorization_code");

        try {
            ResponseEntity<Map> response =
                    restTemplate.postForEntity(GOOGLE_TOKEN_ENDPOINT, body, Map.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new AuthException(HttpStatus.BAD_GATEWAY,
                        "Failed to exchange authorization code with Google");
            }
            return response.getBody();
        } catch (RestClientException ex) {
            log.warn("Google token exchange failed: {}", ex.getMessage());
            throw new AuthException(HttpStatus.BAD_GATEWAY,
                    "Failed to reach Google's token endpoint");
        }
    }

    @SuppressWarnings("unchecked")
    private GoogleUserInfo fetchGoogleUserInfo(String googleAccessToken) {
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                    GOOGLE_USERINFO_ENDPOINT + "?access_token=" + encode(googleAccessToken),
                    Map.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new AuthException(HttpStatus.BAD_GATEWAY,
                        "Failed to fetch user info from Google");
            }
            Map<String, Object> body = response.getBody();
            String sub = (String) body.get("sub");        // unique Google user ID
            String email = (String) body.get("email");
            String name = (String) body.getOrDefault("name", "");
            String givenName = (String) body.getOrDefault("given_name", "");
            String familyName = (String) body.getOrDefault("family_name", "");
            if (email == null || email.isBlank()) {
                throw new AuthException(HttpStatus.BAD_GATEWAY,
                        "Google did not return an email address");
            }
            return new GoogleUserInfo(sub, email, name, givenName, familyName);
        } catch (RestClientException ex) {
            log.warn("Google userinfo fetch failed: {}", ex.getMessage());
            throw new AuthException(HttpStatus.BAD_GATEWAY,
                    "Failed to reach Google's userinfo endpoint");
        }
    }

    /**
     * Finds an existing account by email or creates a new CUSTOMER account.
     * Existing non-OAuth accounts are accepted as-is (allows linking Google sign-in
     * to a password account with the same email).
     */
    private UserAccount upsertGoogleUser(GoogleUserInfo userInfo) {
        String normalizedEmail = userInfo.email().trim().toLowerCase(Locale.ROOT);

        Optional<UserAccount> existing =
                userAccountRepository.findByEmailIgnoreCase(normalizedEmail);
        if (existing.isPresent()) {
            return existing.get();
        }

        // Derive a unique username from the email local-part; append a suffix if taken.
        String base = normalizedEmail.split("@")[0]
                .replaceAll("[^a-z0-9._-]", "")
                .toLowerCase(Locale.ROOT);
        if (base.isBlank()) base = "user";
        String username = findAvailableUsername(base);

        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setEmail(normalizedEmail);
        // Derive first/last name from Google profile; fall back to email local-part.
        user.setFirstName(userInfo.givenName().isBlank() ? base : userInfo.givenName());
        user.setLastName(userInfo.familyName().isBlank() ? "" : userInfo.familyName());
        // OAuth accounts cannot log in with a password.  Store an unreproducible placeholder.
        user.setPasswordHash(OAUTH_PLACEHOLDER_PREFIX + UUID.randomUUID());
        user.setRole(UserRole.CUSTOMER);
        user.setStatus(UserStatus.ACTIVE);

        return userAccountRepository.save(user);
    }

    private String findAvailableUsername(String base) {
        if (!userAccountRepository.existsByUsernameIgnoreCase(base)) return base;
        for (int i = 2; i <= 999; i++) {
            String candidate = base + i;
            if (!userAccountRepository.existsByUsernameIgnoreCase(candidate)) return candidate;
        }
        // Extremely unlikely; fall back to a UUID suffix.
        return base + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** Value object for the Google user-info response fields we care about. */
    record GoogleUserInfo(String sub, String email, String name,
                          String givenName, String familyName) {
    }
}

