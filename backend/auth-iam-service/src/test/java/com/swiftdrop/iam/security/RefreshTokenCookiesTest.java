package com.swiftdrop.iam.security;

import com.swiftdrop.iam.config.JwtConfig;
import com.swiftdrop.iam.dto.TokenResponse;
import com.swiftdrop.iam.exception.AuthException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefreshTokenCookiesTest {

    private static final String APP = "https://swiftdrop.bitcraft.biz";

    private final RefreshTokenCookies cookies = new RefreshTokenCookies(true, APP, jwtConfig(14));

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
        RefreshTokenCookies local = new RefreshTokenCookies(false, "http://localhost:5173", jwtConfig(14));
        assertThat(local.clearHeader()).doesNotContain("Secure");
    }

    @Test
    void resolvePrefersBodyThenCookie() {
        MockHttpServletRequest request = cookieRequest(APP);

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

    @Test
    void cookieFromASiblingSubdomainIsRejected() {
        // SameSite=Strict still sends the cookie from x.bitcraft.biz (same site); the Origin check stops it.
        assertThatThrownBy(() -> cookies.resolve(null, cookieRequest("https://x.bitcraft.biz")))
                .isInstanceOfSatisfying(AuthException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getCode()).isEqualTo(RefreshTokenCookies.ORIGIN_NOT_ALLOWED);
                });
    }

    @Test
    void cookieWithoutOriginIsRejected() {
        assertThatThrownBy(() -> cookies.resolve(null, cookieRequest(null)))
                .isInstanceOf(AuthException.class);
    }

    @Test
    void cookieLookalikeOriginsAreRejected() {
        for (String origin : new String[] {"http://swiftdrop.bitcraft.biz", "https://swiftdrop.bitcraft.biz.evil.example",
                "https://evil-swiftdrop.bitcraft.biz", "null"}) {
            assertThatThrownBy(() -> cookies.resolve(null, cookieRequest(origin)))
                    .as(origin).isInstanceOf(AuthException.class);
        }
    }

    @Test
    void bodyTokenNeedsNoOrigin() {
        // Non-browser clients send the token explicitly; it is not an ambient credential.
        assertThat(cookies.resolve("from-body", cookieRequest(null))).isEqualTo("from-body");
        assertThat(cookies.resolve("from-body", cookieRequest("https://x.bitcraft.biz"))).isEqualTo("from-body");
    }

    @Test
    void noCookieAndNoBodyStaysNullWhateverTheOrigin() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.ORIGIN, "https://x.bitcraft.biz");
        assertThat(cookies.resolve(null, request)).isNull();
    }

    @Test
    void allowedOriginsAreCommaSeparatedAndNormalised() {
        RefreshTokenCookies multi = new RefreshTokenCookies(true,
                " https://swiftdrop.bitcraft.biz/ , http://localhost:5173 ", jwtConfig(14));

        assertThat(multi.resolve(null, cookieRequest("https://SwiftDrop.bitcraft.biz"))).isEqualTo("from-cookie");
        assertThat(multi.resolve(null, cookieRequest("http://localhost:5173"))).isEqualTo("from-cookie");
        assertThat(multi.isAllowedOrigin("http://localhost:3000")).isFalse();
    }

    private static MockHttpServletRequest cookieRequest(String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(RefreshTokenCookies.COOKIE_NAME, "from-cookie"));
        if (origin != null) {
            request.addHeader(HttpHeaders.ORIGIN, origin);
        }
        return request;
    }

    private static JwtConfig jwtConfig(long refreshDays) {
        JwtConfig config = new JwtConfig();
        config.setRefreshTokenExpirationDays(refreshDays);
        return config;
    }
}
