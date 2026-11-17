package com.swiftdrop.iam.security;

import com.swiftdrop.iam.config.JwtConfig;
import com.swiftdrop.iam.dto.TokenResponse;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenCookiesTest {

    private final RefreshTokenCookies cookies = new RefreshTokenCookies(true, jwtConfig(14));

    @Test
    void issueMovesRefreshTokenFromBodyIntoHardenedCookie() {
        TokenResponse tokens = new TokenResponse("access", "refresh-secret", "Bearer", 3600,
                "user-1", "alice", "alice@example.com", null, null);

        ResponseEntity<TokenResponse> response = cookies.issue(tokens);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().refreshToken()).isNull();
        assertThat(response.getBody().accessToken()).isEqualTo("access");
        String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie)
                .startsWith(RefreshTokenCookies.COOKIE_NAME + "=refresh-secret")
                .contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/v1/auth")
                .contains("Max-Age=" + 14 * 24 * 3600);
    }

    @Test
    void clearHeaderExpiresTheCookie() {
        assertThat(cookies.clearHeader())
                .startsWith(RefreshTokenCookies.COOKIE_NAME + "=;")
                .contains("Max-Age=0", "HttpOnly", "Path=/api/v1/auth");
    }

    @Test
    void secureFlagCanBeDisabledForPlainHttpLocalRuns() {
        RefreshTokenCookies local = new RefreshTokenCookies(false, jwtConfig(14));
        assertThat(local.clearHeader()).doesNotContain("Secure");
    }

    @Test
    void resolvePrefersBodyThenCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(RefreshTokenCookies.COOKIE_NAME, "from-cookie"));

        assertThat(cookies.resolve("from-body", request)).isEqualTo("from-body");
        assertThat(cookies.resolve(null, request)).isEqualTo("from-cookie");
        assertThat(cookies.resolve("  ", request)).isEqualTo("from-cookie");
    }

    @Test
    void resolveReturnsNullWhenNothingWasSent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThat(cookies.resolve(null, request)).isNull();

        request.setCookies(new Cookie("other", "x"));
        assertThat(cookies.resolve(null, request)).isNull();
    }

    private static JwtConfig jwtConfig(long refreshDays) {
        JwtConfig config = new JwtConfig();
        config.setRefreshTokenExpirationDays(refreshDays);
        return config;
    }
}
