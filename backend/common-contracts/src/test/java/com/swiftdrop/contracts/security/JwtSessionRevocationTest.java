package com.swiftdrop.contracts.security;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JwtSessionRevocationTest {
    private static final Instant CUTOFF = Instant.parse("2026-11-02T00:00:00.500Z");

    @Test
    void distinguishesSessionsIssuedWithinTheSameSecond() {
        assertThat(JwtSessionRevocation.isRevoked(CUTOFF.toString(), "2026-11-02T00:00:00.100Z", null)).isTrue();
        assertThat(JwtSessionRevocation.isRevoked(CUTOFF.toString(), "2026-11-02T00:00:00.900Z", null)).isFalse();
    }

    @Test
    void rejectsTokensAtTheCutoffAndWithoutAnIssuanceTime() {
        assertThat(JwtSessionRevocation.isRevoked(CUTOFF.toString(), CUTOFF.toString(), null)).isTrue();
        assertThat(JwtSessionRevocation.isRevoked(CUTOFF.toString(), null, null)).isTrue();
    }

    @Test
    void fallsBackToStandardIatForExistingTokens() {
        assertThat(JwtSessionRevocation.isRevoked(CUTOFF.toString(), null, CUTOFF.minusSeconds(1))).isTrue();
        assertThat(JwtSessionRevocation.isRevoked(CUTOFF.toString(), null, CUTOFF.plusSeconds(1))).isFalse();
    }

    @Test
    void preservesLegacyBlacklistMarkersAndRejectsMalformedTimes() {
        assertThat(JwtSessionRevocation.isRevoked("1", CUTOFF.plusSeconds(1).toString(), null)).isTrue();
        assertThat(JwtSessionRevocation.isRevoked("invalid", CUTOFF.toString(), null)).isTrue();
        assertThat(JwtSessionRevocation.isRevoked(CUTOFF.toString(), "invalid", null)).isTrue();
    }

    @Test
    void allowsSessionsWhenNoRevocationExists() {
        assertThat(JwtSessionRevocation.isRevoked(null, null, null)).isFalse();
    }
}
