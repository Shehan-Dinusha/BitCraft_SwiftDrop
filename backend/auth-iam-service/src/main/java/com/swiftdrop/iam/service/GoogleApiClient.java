package com.swiftdrop.iam.service;

import com.swiftdrop.iam.config.GoogleOAuthConfig;
import com.swiftdrop.iam.exception.AuthException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * HTTP calls to Google's OAuth 2.0 endpoints (Adapter over Google's REST API). Kept apart from
 * {@link GoogleOAuthService} so the sign-in flow logic does not depend on HTTP details.
 */
@Slf4j
@Component
public class GoogleApiClient {

    private static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    private static final String USERINFO_ENDPOINT = "https://www.googleapis.com/oauth2/v3/userinfo";

    private final GoogleOAuthConfig oauthConfig;
    private final RestTemplate restTemplate;

    public GoogleApiClient(GoogleOAuthConfig oauthConfig, RestTemplate restTemplate) {
        this.oauthConfig = oauthConfig;
        this.restTemplate = restTemplate;
    }

    /**
     * Exchanges Google's authorization code for an access token, server to server, so the
     * client secret never reaches the browser.
     *
     * @return Google's access token
     * @throws AuthException 502 when Google rejects the code or cannot be reached
     */
    // RestTemplate maps the JSON body to a raw Map when given Map.class.
    @SuppressWarnings("rawtypes")
    public String exchangeCode(String code) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("code", code);
        body.add("client_id", oauthConfig.getClientId());
        body.add("client_secret", oauthConfig.getClientSecret());
        body.add("redirect_uri", oauthConfig.getRedirectUri());
        body.add("grant_type", "authorization_code");

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(TOKEN_ENDPOINT, body, Map.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new AuthException(HttpStatus.BAD_GATEWAY,
                        "Failed to exchange authorization code with Google");
            }
            Object accessToken = response.getBody().get("access_token");
            if (!(accessToken instanceof String token) || token.isBlank()) {
                throw new AuthException(HttpStatus.BAD_GATEWAY, "Google returned no access token");
            }
            return token;
        } catch (RestClientException ex) {
            log.warn("Google token exchange failed: {}", ex.getMessage());
            throw new AuthException(HttpStatus.BAD_GATEWAY,
                    "Failed to reach Google's token endpoint");
        }
    }

    /**
     * Reads the signed-in Google user's profile. Rejects accounts whose email Google has not verified.
     *
     * @throws AuthException 502 when Google cannot be reached; 403 ({@code email_unverified}) for an
     *                       unverified email
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public GoogleUserInfo fetchUserInfo(String googleAccessToken) {
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                    USERINFO_ENDPOINT + "?access_token=" + encode(googleAccessToken),
                    Map.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new AuthException(HttpStatus.BAD_GATEWAY,
                        "Failed to fetch user info from Google");
            }
            Map<String, Object> body = response.getBody();
            String sub = (String) body.get("sub"); // unique Google user ID
            String email = (String) body.get("email");
            String name = (String) body.getOrDefault("name", "");
            String givenName = (String) body.getOrDefault("given_name", "");
            String familyName = (String) body.getOrDefault("family_name", "");
            if (email == null || email.isBlank()) {
                throw new AuthException(HttpStatus.BAD_GATEWAY,
                        "Google did not return an email address");
            }
            Object verifiedClaim = body.get("email_verified");
            boolean emailVerified = Boolean.TRUE.equals(verifiedClaim)
                    || "true".equalsIgnoreCase(String.valueOf(verifiedClaim));
            if (!emailVerified) {
                throw new AuthException(HttpStatus.FORBIDDEN,
                        "Your Google email address is not verified", "email_unverified");
            }
            return new GoogleUserInfo(sub, email, name, givenName, familyName, true);
        } catch (RestClientException ex) {
            log.warn("Google userinfo fetch failed: {}", ex.getMessage());
            throw new AuthException(HttpStatus.BAD_GATEWAY,
                    "Failed to reach Google's userinfo endpoint");
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** Value object for the Google user-info response fields we care about. */
    public record GoogleUserInfo(String sub, String email, String name,
            String givenName, String familyName, boolean emailVerified) {
    }
}
