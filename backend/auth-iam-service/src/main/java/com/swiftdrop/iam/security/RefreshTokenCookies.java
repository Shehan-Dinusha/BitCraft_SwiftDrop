package com.swiftdrop.iam.security;

import com.swiftdrop.iam.config.JwtConfig;
import com.swiftdrop.iam.dto.TokenResponse;
import com.swiftdrop.iam.exception.AuthException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Carries the refresh token in an {@code HttpOnly} cookie instead of the JSON body.
 *
 * <p>Why: a refresh token lives for days. Anything the browser keeps in JavaScript-readable
 * storage can be stolen by a single XSS bug, which would give the attacker a long-lived
 * session. An {@code HttpOnly} cookie cannot be read by scripts, {@code SameSite=Strict}
 * stops other sites from sending it, and the narrow {@code Path} means the browser only
 * attaches it to the auth endpoints that need it.
 *
 * <p>Non-browser clients can still send the token in the request body of
 * {@code /refresh} and {@code /logout}; the cookie is only a second, preferred source.
 *
 * <p>Origin check: {@code SameSite=Strict} is per <em>site</em> (registrable domain), so a page
 * on any other subdomain of the same domain still gets the cookie attached. A token taken from
 * the cookie is therefore only accepted when the request's {@code Origin} header (set by the
 * browser, not forgeable by page scripts) is one of the configured frontend origins. Tokens sent
 * in the body are unaffected: they are not ambient credentials, so they cannot be abused this way.
 */
@Component
public class RefreshTokenCookies {

    /** Cookie name. The {@code __Host-} prefix is not used because the cookie is path-scoped. */
    public static final String COOKIE_NAME = "swiftdrop_refresh";

    /** Only the auth endpoints receive the cookie (the browser path, as seen through the gateway). */
    static final String COOKIE_PATH = "/api/v1/auth";

    /** Error code returned when a cookie-authenticated request comes from another origin. */
    public static final String ORIGIN_NOT_ALLOWED = "origin_not_allowed";

    private final boolean secure;
    private final Duration maxAge;
    private final List<String> allowedOrigins;

    public RefreshTokenCookies(
            @Value("${swiftdrop.auth.refresh-cookie.secure:true}") boolean secure,
            @Value("${swiftdrop.auth.refresh-cookie.allowed-origins:http://localhost:5173}") String allowedOrigins,
            JwtConfig jwtConfig) {
        this.secure = secure;
        this.maxAge = Duration.ofDays(jwtConfig.getRefreshTokenExpirationDays());
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(RefreshTokenCookies::normalize)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }

    /**
     * Builds a 200 response that sets the refresh cookie and leaves the refresh token
     * out of the body, so browser JavaScript never sees it.
     *
     * @param tokens the freshly issued token pair
     * @return the response to return from the controller
     */
    public ResponseEntity<TokenResponse> issue(TokenResponse tokens) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie(tokens.refreshToken(), maxAge).toString())
                .body(withoutRefreshToken(tokens));
    }

    /**
     * @return a {@code Set-Cookie} header value that deletes the refresh cookie
     */
    public String clearHeader() {
        return cookie("", Duration.ZERO).toString();
    }

    /**
     * Picks the refresh token from the request body when present, otherwise from the cookie.
     * A token from the cookie is only returned for requests from an allowed frontend origin.
     *
     * @param fromBody token sent in the JSON body, may be {@code null} or blank
     * @param request  the current request
     * @return the refresh token, or {@code null} when the client sent none
     * @throws AuthException 403 ({@value #ORIGIN_NOT_ALLOWED}) when the cookie arrives from a
     *                       missing or foreign {@code Origin}
     */
    public String resolve(String fromBody, HttpServletRequest request) {
        if (fromBody != null && !fromBody.isBlank()) {
            return fromBody;
        }
        String fromCookie = cookieValue(request);
        if (fromCookie != null && !isAllowedOrigin(request.getHeader(HttpHeaders.ORIGIN))) {
            throw new AuthException(HttpStatus.FORBIDDEN, "Request origin is not allowed", ORIGIN_NOT_ALLOWED);
        }
        return fromCookie;
    }

    /** @return whether {@code origin} is one of the configured frontend origins */
    boolean isAllowedOrigin(String origin) {
        return origin != null && allowedOrigins.contains(normalize(origin));
    }

    private static String cookieValue(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /** Lower-cases and drops a trailing slash, so "https://App.example/" equals "https://app.example". */
    private static String normalize(String origin) {
        String trimmed = origin.trim().toLowerCase(Locale.ROOT);
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private ResponseCookie cookie(String value, Duration age) {
        return ResponseCookie.from(COOKIE_NAME, value == null ? "" : value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(age)
                .build();
    }

    private static TokenResponse withoutRefreshToken(TokenResponse tokens) {
        return new TokenResponse(
                tokens.accessToken(),
                null,
                tokens.tokenType(),
                tokens.expiresIn(),
                tokens.userId(),
                tokens.username(),
                tokens.email(),
                tokens.role(),
                tokens.district());
    }
}
