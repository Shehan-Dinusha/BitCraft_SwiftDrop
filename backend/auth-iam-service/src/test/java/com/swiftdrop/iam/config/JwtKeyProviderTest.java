package com.swiftdrop.iam.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.iam.support.RsaTestKeys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

import java.math.BigInteger;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtKeyProviderTest {
    @Test
    void defaultModeExposesNoSymmetricSecret() throws Exception {
        var config = new JwtConfig();
        var keys = new JwtKeyProvider(config, new MockEnvironment());
        assertThat(keys.isRsaEnabled()).isFalse();
        assertThat(keys.jwks().keys()).isEmpty();
        assertThat(new ObjectMapper().writeValueAsString(keys.jwks())).isEqualTo("{\"keys\":[]}");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "RS512", "HS512", "none", "rs256"})
    void rejectsUnsupportedAlgorithms(String algorithm) {
        var config = new JwtConfig();
        config.setAlgorithm(algorithm);
        assertThatThrownBy(() -> new JwtKeyProvider(config, new MockEnvironment())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void loadsStableMatchingPemPairAndPublishesOnlyPublicParameters() throws Exception {
        var config = RsaTestKeys.config(RsaTestKeys.PRIMARY);
        var keys = new JwtKeyProvider(config, new MockEnvironment());
        var sameKeys = new JwtKeyProvider(config, new MockEnvironment());
        assertThat(keys.keyId()).isEqualTo(sameKeys.keyId());
        var jwk = keys.jwks().keys().getFirst();
        assertThat(jwk.kty()).isEqualTo("RSA");
        assertThat(jwk.alg()).isEqualTo("RS256");
        assertThat(jwk.use()).isEqualTo("sig");
        assertThat(jwk.kid()).isEqualTo(keys.keyId());
        assertThat(jwk.n()).doesNotContain("=");
        assertThat(jwk.e()).isEqualTo("AQAB");
        assertThat(new BigInteger(1, Base64.getUrlDecoder().decode(jwk.n())))
                .isEqualTo(((RSAPublicKey) RsaTestKeys.PRIMARY.getPublic()).getModulus());
        var fields = new ObjectMapper().valueToTree(jwk);
        assertThat(fields.size()).isEqualTo(6);
        for (String privateField : new String[]{"d", "p", "q", "dp", "dq", "qi", "k"}) {
            assertThat(fields.has(privateField)).isFalse();
        }
    }

    @Test
    void supportsLiteralEscapedPemNewlines() {
        var config = RsaTestKeys.config(RsaTestKeys.PRIMARY);
        config.setPrivateKey(config.getPrivateKey().replace("\n", "\\n"));
        config.setPublicKey(config.getPublicKey().replace("\n", "\\n"));
        assertThat(new JwtKeyProvider(config, new MockEnvironment()).keyId()).isNotBlank();
    }

    @Test
    void canPublishConfiguredRsaKeyBeforeEnablingRsaSigning() {
        var config = RsaTestKeys.config(RsaTestKeys.PRIMARY);
        config.setAlgorithm("HS256");
        var keys = new JwtKeyProvider(config, new MockEnvironment());
        assertThat(keys.isRsaEnabled()).isFalse();
        assertThat(keys.jwks().keys()).hasSize(1);
    }

    @Test
    void rejectsMismatchedPair() {
        var config = RsaTestKeys.config(RsaTestKeys.PRIMARY);
        config.setPublicKey(RsaTestKeys.config(RsaTestKeys.OTHER).getPublicKey());
        assertThatThrownBy(() -> new JwtKeyProvider(config, new MockEnvironment())).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("matching pair");
    }

    @Test
    void rejectsWeakRsaPair() {
        var config = RsaTestKeys.config(RsaTestKeys.generate(1024));
        assertThatThrownBy(() -> new JwtKeyProvider(config, new MockEnvironment())).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("2048");
    }

    @Test
    void rejectsPartialPairEvenInDev() {
        var config = RsaTestKeys.config(RsaTestKeys.PRIMARY);
        config.setPublicKey("");
        assertThatThrownBy(() -> new JwtKeyProvider(config, new MockEnvironment().withProperty("spring.profiles.active", "dev")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Both");
    }

    @Test
    void malformedKeysHaveSafeErrorsWithoutTheirContentsOrCauses() {
        var config = RsaTestKeys.config(RsaTestKeys.PRIMARY);
        config.setPrivateKey("-----BEGIN PRIVATE KEY-----\nsecret-invalid-material\n-----END PRIVATE KEY-----");
        assertThatThrownBy(() -> new JwtKeyProvider(config, new MockEnvironment())).isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("secret-invalid-material").hasNoCause();
    }

    @Test
    void missingKeysFailOutsideExplicitDev() {
        var config = new JwtConfig();
        config.setAlgorithm("RS256");
        assertThatThrownBy(() -> new JwtKeyProvider(config, new MockEnvironment())).isInstanceOf(IllegalStateException.class);
        var aks = new MockEnvironment();
        aks.setActiveProfiles("aks");
        assertThatThrownBy(() -> new JwtKeyProvider(config, aks)).isInstanceOf(IllegalStateException.class);
        aks.setActiveProfiles("dev", "aks");
        assertThatThrownBy(() -> new JwtKeyProvider(config, aks)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void explicitDevGeneratesAnEphemeralPair() {
        var config = new JwtConfig();
        config.setAlgorithm("RS256");
        var environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        var keys = new JwtKeyProvider(config, environment);
        assertThat(keys.verificationKey().getModulus().bitLength()).isGreaterThanOrEqualTo(2048);
        assertThat(keys.jwks().keys()).hasSize(1);
    }
}
