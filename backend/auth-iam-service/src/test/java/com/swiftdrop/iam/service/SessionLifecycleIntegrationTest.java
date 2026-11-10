package com.swiftdrop.iam.service;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.iam.config.JwtConfig;
import com.swiftdrop.iam.dto.ChangePasswordRequest;
import com.swiftdrop.iam.dto.RegisterUserRequest;
import com.swiftdrop.iam.dto.TokenResponse;
import com.swiftdrop.iam.exception.InvalidRefreshTokenException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Uses the existing IAM PostgreSQL and Redis; never starts additional containers. */
@EnabledIfSystemProperty(named = "swiftdrop.integration-tests", matches = "true")
@SpringBootTest(properties = {
        "spring.rabbitmq.dynamic=false",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.listener.direct.auto-startup=false",
        "swiftdrop.jwt.blacklist-prefix=jwt:m2-regression:",
        "swiftdrop.bootstrap.admin.username=",
        "swiftdrop.bootstrap.admin.password="
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class SessionLifecycleIntegrationTest {

    @Autowired private AuthenticationService authentication;
    @Autowired private UserAccountService users;
    @Autowired private TokenIssuanceService tokens;
    @Autowired private JwtConfig jwtConfig;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StringRedisTemplate redis;
    @Autowired private MockMvc mvc;

    private UUID userId;
    private String username;
    private String password;
    private final List<TokenResponse> sessions = new ArrayList<>();

    @BeforeEach
    void createFixture() {
        username = "m2reg." + UUID.randomUUID().toString().replace("-", "");
        password = "Regression7!" + UUID.randomUUID();
        userId = users.register(new RegisterUserRequest(username, username + "@example.invalid", null,
                "Regression", "Test", password, UserRole.CUSTOMER, SriLankanDistrict.COLOMBO, null)).id();
    }

    @AfterEach
    void removeOnlyThisTestFixture() {
        if (userId == null) return;
        for (TokenResponse session : sessions) {
            var claims = tokens.parse(session.accessToken());
            redis.delete(List.of(jwtConfig.getBlacklistPrefix() + "token:" + tokens.hashToken(session.accessToken()),
                    jwtConfig.getBlacklistPrefix() + "jti:" + claims.getId()));
        }
        redis.delete(jwtConfig.getBlacklistPrefix() + "user:" + userId);
        jdbc.update("delete from refresh_tokens where user_id in (select id from user_accounts where public_id = ? and username = ?)", userId, username);
        jdbc.update("delete from role_assignments where user_id in (select id from user_accounts where public_id = ? and username = ?)", userId, username);
        jdbc.update("delete from user_accounts where public_id = ? and username = ?", userId, username);
    }

    @Test
    void replayRevocationCommitsAfterTheRefreshRequestThrows() throws Exception {
        TokenResponse original = login(password);
        TokenResponse replacement = refresh(original.refreshToken());

        assertThatThrownBy(() -> authentication.refresh(original.refreshToken(), "127.0.0.1", "regression"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        assertThat(jdbc.queryForObject("select count(*) from refresh_tokens r join user_accounts u on u.id = r.user_id where u.public_id = ? and r.revoked = false", Long.class, userId))
                .as("replay revocation must remain committed outside the failed request")
                .isZero();
        assertThatThrownBy(() -> authentication.refresh(replacement.refreshToken(), "127.0.0.1", "regression"))
                .isInstanceOf(InvalidRefreshTokenException.class);
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + replacement.accessToken()))
                .andExpect(status().isUnauthorized());

        // A legitimate new login after the replay incident must remain usable.
        TokenResponse fresh = login(password);
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + fresh.accessToken()))
                .andExpect(status().isOk());
    }

    @Test
    void passwordChangeRejectsOldSessionsButAllowsImmediateNewLogin() throws Exception {
        TokenResponse original = login(password);
        String newPassword = "Replacement8!" + UUID.randomUUID();
        users.changePassword(userId, new ChangePasswordRequest(password, newPassword));

        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + original.accessToken()))
                .andExpect(status().isUnauthorized());
        assertThatThrownBy(() -> authentication.refresh(original.refreshToken(), "127.0.0.1", "regression"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        TokenResponse fresh = login(newPassword);
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + fresh.accessToken()))
                .andExpect(status().isOk());
    }

    @Test
    void expiredRefreshTokenRevocationAlsoCommitsOnRejection() {
        TokenResponse original = login(password);
        String hash = tokens.hashToken(original.refreshToken());
        jdbc.update("update refresh_tokens set expires_at = current_timestamp - interval '1 minute' where token = ?", hash);

        assertThatThrownBy(() -> authentication.refresh(original.refreshToken(), "127.0.0.1", "regression"))
                .isInstanceOf(InvalidRefreshTokenException.class);
        assertThat(jdbc.queryForObject("select revoked from refresh_tokens where token = ?", Boolean.class, hash))
                .isTrue();
    }

    private TokenResponse login(String rawPassword) {
        TokenResponse session = authentication.login(username, rawPassword, "127.0.0.1", "regression");
        sessions.add(session);
        return session;
    }

    private TokenResponse refresh(String refreshToken) {
        TokenResponse session = authentication.refresh(refreshToken, "127.0.0.1", "regression");
        sessions.add(session);
        return session;
    }
}
