package com.swiftdrop.iam.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.iam.config.JwtConfig;
import com.swiftdrop.iam.config.JwtKeyProvider;
import com.swiftdrop.iam.dto.RegisterUserRequest;
import com.swiftdrop.iam.dto.TokenResponse;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyFactory;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Real HTTP RSA tests on a temporary host port using only the existing IAM PostgreSQL/Redis. */
@EnabledIfSystemProperty(named = "swiftdrop.integration-tests", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.rabbitmq.dynamic=false",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.listener.direct.auto-startup=false",
        "swiftdrop.jwt.algorithm=RS256",
        "swiftdrop.jwt.private-key=",
        "swiftdrop.jwt.public-key=",
        "swiftdrop.jwt.blacklist-prefix=jwt:m2-rsa:",
        "swiftdrop.bootstrap.admin.username=",
        "swiftdrop.bootstrap.admin.password="
})
@ActiveProfiles("dev")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RsaSessionIntegrationTest {
    @LocalServerPort private int port;
    @Autowired private UserAccountService users;
    @Autowired private TokenIssuanceService tokens;
    @Autowired private JwtKeyProvider keys;
    @Autowired private JwtConfig config;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StringRedisTemplate redis;
    @Autowired private ObjectMapper json;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final List<TokenResponse> sessions = new ArrayList<>();
    private UUID id;
    private String username;
    private String password;

    @BeforeEach
    void fixture() {
        username = "m2rsa." + UUID.randomUUID().toString().replace("-", "");
        password = "RsaPassword7!" + UUID.randomUUID();
        id = users.register(new RegisterUserRequest(username, username + "@example.invalid", null,
                "RSA", "Test", password, UserRole.CUSTOMER, SriLankanDistrict.COLOMBO, null)).id();
    }

    @AfterEach
    void cleanupOwnFixture() {
        if (id == null) return;
        for (var session : sessions) {
            var claims = tokens.parse(session.accessToken());
            redis.delete(List.of(config.getBlacklistPrefix() + "token:" + tokens.hashToken(session.accessToken()),
                    config.getBlacklistPrefix() + "jti:" + claims.getId()));
        }
        redis.delete(config.getBlacklistPrefix() + "user:" + id);
        jdbc.update("delete from refresh_tokens where user_id in (select id from user_accounts where public_id=? and username=?)", id, username);
        jdbc.update("delete from role_assignments where user_id in (select id from user_accounts where public_id=? and username=?)", id, username);
        jdbc.update("delete from user_accounts where public_id=? and username=?", id, username);
    }

    @Test
    void anonymousJwksVerifiesRealLoginAndLogoutRevokesTheSession() throws Exception {
        var response = request("GET", "/api/v1/auth/.well-known/jwks.json", null, null, 200);
        assertThat(response.headers().firstValue("Cache-Control").orElseThrow()).contains("max-age=300", "public");
        var jwks = json.readTree(response.body());
        assertThat(jwks.path("keys").size()).isEqualTo(1);
        var jwk = jwks.path("keys").get(0);
        assertThat(jwk.size()).isEqualTo(6);
        assertThat(jwk.path("kty").asText()).isEqualTo("RSA");
        assertThat(jwk.path("alg").asText()).isEqualTo("RS256");
        var publicKey = KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(
                new BigInteger(1, Base64.getUrlDecoder().decode(jwk.path("n").asText())),
                new BigInteger(1, Base64.getUrlDecoder().decode(jwk.path("e").asText()))));
        var session = login(password);
        var verified = Jwts.parser().verifyWith(publicKey).requireIssuer(config.getIssuer())
                .requireAudience(config.getAudience()).build().parseSignedClaims(session.accessToken());
        assertThat(verified.getHeader().getAlgorithm()).isEqualTo("RS256");
        assertThat(verified.getHeader().getKeyId()).isEqualTo(jwk.path("kid").asText());
        assertThat(verified.getPayload().getSubject()).isEqualTo(id.toString());
        request("GET", "/api/v1/users/me", null, session.accessToken(), 200);
        request("POST", "/api/v1/auth/logout", Map.of("refreshToken", session.refreshToken()), session.accessToken(), 200);
        request("GET", "/api/v1/users/me", null, session.accessToken(), 401);
        request("POST", "/api/v1/auth/refresh", Map.of("refreshToken", session.refreshToken()), null, 401);
        request("GET", "/api/v1/users/me", null, login(password).accessToken(), 200);
    }

    @Test
    void refreshReplayAndPasswordChangePreserveExistingRsaSessionGuarantees() throws Exception {
        var first = login(password);
        var refreshed = readSession(request("POST", "/api/v1/auth/refresh", Map.of("refreshToken", first.refreshToken()), null, 200));
        request("GET", "/api/v1/users/me", null, refreshed.accessToken(), 200);
        request("POST", "/api/v1/auth/refresh", Map.of("refreshToken", first.refreshToken()), null, 401);
        request("POST", "/api/v1/auth/refresh", Map.of("refreshToken", refreshed.refreshToken()), null, 401);
        request("GET", "/api/v1/users/me", null, refreshed.accessToken(), 401);
        var fresh = login(password);
        String replacement = "Replacement8!" + UUID.randomUUID();
        request("POST", "/api/v1/auth/change-password", Map.of("currentPassword", password, "newPassword", replacement), fresh.accessToken(), 200);
        request("GET", "/api/v1/users/me", null, fresh.accessToken(), 401);
        request("GET", "/api/v1/users/me", null, login(replacement).accessToken(), 200);
    }

    @Test
    void invalidIssuerAndUnknownKeyIdAreUnauthorizedOverHttp() throws Exception {
        var issuer = Jwts.builder().issuer("other").audience().add(config.getAudience()).and()
                .subject(id.toString()).claim("role", "CUSTOMER").expiration(Date.from(Instant.now().plusSeconds(60)))
                .header().keyId(keys.keyId()).and().signWith(keys.signingKey(), Jwts.SIG.RS256).compact();
        var kid = Jwts.builder().issuer(config.getIssuer()).audience().add(config.getAudience()).and()
                .subject(id.toString()).claim("role", "CUSTOMER").expiration(Date.from(Instant.now().plusSeconds(60)))
                .header().keyId("unknown").and().signWith(keys.signingKey(), Jwts.SIG.RS256).compact();
        request("GET", "/api/v1/users/me", null, issuer, 401);
        request("GET", "/api/v1/users/me", null, kid, 401);
    }

    @Test
    void liveOpenApiMarksJwksAsAnonymousAndStandardUnwrappedDocument() throws Exception {
        JsonNode document = json.readTree(request("GET", "/v3/api-docs", null, null, 200).body());
        var operation = document.path("paths").path("/api/v1/auth/.well-known/jwks.json").path("get");
        assertThat(operation.isMissingNode()).isFalse();
        assertThat(operation.path("security").size()).isZero();
        assertThat(document.path("components").path("schemas").path("JwksResponse").path("properties").has("keys")).isTrue();
    }

    private TokenResponse login(String secret) throws Exception {
        return readSession(request("POST", "/api/v1/auth/login", Map.of("username", username, "password", secret), null, 200));
    }

    private TokenResponse readSession(HttpResponse<String> response) throws Exception {
        var session = json.readValue(response.body(), TokenResponse.class);
        sessions.add(session);
        return session;
    }

    private HttpResponse<String> request(String method, String path, Object body, String token, int expected) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        var response = client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(method + " " + path).isEqualTo(expected);
        return response;
    }
}
