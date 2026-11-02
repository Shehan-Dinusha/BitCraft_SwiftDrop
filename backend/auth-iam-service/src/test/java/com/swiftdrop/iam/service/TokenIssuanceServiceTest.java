package com.swiftdrop.iam.service;

import com.swiftdrop.contracts.security.JwtSessionRevocation;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.iam.config.JwtConfig;
import com.swiftdrop.iam.config.JwtKeyProvider;
import org.springframework.mock.env.MockEnvironment;
import com.swiftdrop.iam.domain.entity.UserAccount;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TokenIssuanceServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> values;

    private TokenIssuanceService tokenIssuanceService;
    private UserAccount user;

    @BeforeEach
    void setUp() {
        JwtConfig config = new JwtConfig();
        config.setSecret("test-secret-key-that-is-long-enough-for-hs256-signing!!");
        config.setIssuer("swiftdrop-iam");
        config.setAudience("swiftdrop");
        config.setAccessTokenExpirationMs(3_600_000L);

        tokenIssuanceService = new TokenIssuanceService(config, redisTemplate, new JwtKeyProvider(config, new MockEnvironment()));

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
        assertThat(Instant.parse(claims.get(JwtSessionRevocation.ISSUED_AT_CLAIM, String.class)))
                .isEqualTo(issued.expiresAt().minusSeconds(3600));
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

    @Test
    void revokesOnlyTokensIssuedBeforeTheAccountChange() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        var issued = tokenIssuanceService.issueAccessToken(user);
        Claims claims = tokenIssuanceService.parse(issued.token());
        Instant preciseIssuance = Instant.parse(claims.get(JwtSessionRevocation.ISSUED_AT_CLAIM, String.class));
        String key = "jwt:blacklist:user:" + user.getPublicId();

        when(values.get(key)).thenReturn(preciseIssuance.plusNanos(1).toString());
        assertThat(tokenIssuanceService.isBlacklisted(issued.token(), claims)).isTrue();
        when(values.get(key)).thenReturn(preciseIssuance.minusNanos(1).toString());
        assertThat(tokenIssuanceService.isBlacklisted(issued.token(), claims)).isFalse();
    }

    @Test
    void storesAnExpiringRevocationCutoffInsteadOfBlockingEveryNewSession() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        Instant before = Instant.now();
        tokenIssuanceService.blacklistUser(user.getPublicId());

        ArgumentCaptor<String> cutoff = ArgumentCaptor.forClass(String.class);
        verify(values).set(eq("jwt:blacklist:user:" + user.getPublicId()), cutoff.capture(), eq(Duration.ofHours(1)));
        assertThat(Instant.parse(cutoff.getValue())).isBetween(before, Instant.now());
    }
}
