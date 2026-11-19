package com.swiftdrop.iam.security;

import com.swiftdrop.iam.config.JwtConfig;
import com.swiftdrop.iam.dto.TokenResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.Duration;

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
 */
@Component
public class RefreshTokenCookies {

    /** Cookie name. The {@code __Host-} prefix is not used because the cookie is path-scoped. */
    public static final String COOKIE_NAME = "swiftdrop_refresh";

    /** Only the auth endpoints receive the cookie (the browser path, as seen through the gateway). */
    static final String COOKIE_PATH = "/api/v1/auth";

    private final boolean secure;
    private final Duration maxAge;

    public RefreshTokenCookies(
            @Value("${swiftdrop.auth.refresh-cookie.secure:true}") boolean secure,
            JwtConfig jwtConfig) {
        this.secure = secure;
        this.maxAge = Duration.ofDays(jwtConfig.getRefreshTokenExpirationDays());
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
     *
     * @param fromBody token sent in the JSON body, may be {@code null} or blank
     * @param request  the current request
     * @return the refresh token, or {@code null} when the client sent none
     */
    public String resolve(String fromBody, HttpServletRequest request) {
        if (fromBody != null && !fromBody.isBlank()) {
            return fromBody;
        }
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
