package com.swiftdrop.iam.service;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.iam.config.JwtConfig;
import com.swiftdrop.iam.domain.entity.UserAccount;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class TokenIssuanceServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private TokenIssuanceService tokenIssuanceService;
    private UserAccount user;

    @BeforeEach
    void setUp() {
        JwtConfig config = new JwtConfig();
        config.setSecret("test-secret-key-that-is-long-enough-for-hs256-signing!!");
        config.setIssuer("swiftdrop-iam");
        config.setAudience("swiftdrop");
        config.setAccessTokenExpirationMs(3_600_000L);

        tokenIssuanceService = new TokenIssuanceService(config, redisTemplate);

        user = new UserAccount();
        user.setPublicId(UUID.randomUUID());
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setRole(UserRole.CUSTOMER);
        user.setDistrict(SriLankanDistrict.COLOMBO);
    }

    @Test
    void issuesSignedTokenWithExpectedClaims() {
        TokenIssuanceService.IssuedAccessToken issued = tokenIssuanceService.issueAccessToken(user);

        Claims claims = tokenIssuanceService.parse(issued.token());

        assertThat(claims.getSubject()).isEqualTo(user.getPublicId().toString());
        assertThat(claims.getIssuer()).isEqualTo("swiftdrop-iam");
        assertThat(claims.getAudience()).contains("swiftdrop");
        assertThat(claims.getId()).isEqualTo(issued.jwtId());
        assertThat(claims.get("role", String.class)).isEqualTo("CUSTOMER");
        assertThat(claims.get("districtId", String.class)).isEqualTo("COLOMBO");
        assertThat(issued.expiresInSeconds()).isEqualTo(3600);
    }

    @Test
    void rejectsTamperedToken() {
        String token = tokenIssuanceService.issueAccessToken(user).token();

        assertThatThrownBy(() -> tokenIssuanceService.parse(token + "tampered"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void hashesTokensDeterministically() {
        String first = tokenIssuanceService.hashToken("refresh-token");
        String second = tokenIssuanceService.hashToken("refresh-token");

        assertThat(first).hasSize(64).isEqualTo(second);
        assertThat(tokenIssuanceService.hashToken("different")).isNotEqualTo(first);
    }
}
