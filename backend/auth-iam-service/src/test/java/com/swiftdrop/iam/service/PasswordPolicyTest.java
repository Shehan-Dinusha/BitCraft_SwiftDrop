package com.swiftdrop.iam.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(12);

    @Test
    void acceptsStrongEnoughPassword() {
        assertThatCode(() -> policy.validate("SwiftDrop123")).doesNotThrowAnyException();
    }

    @Test
    void rejectsShortPassword() {
        assertThatThrownBy(() -> policy.validate("Ab1")).hasMessageContaining("at least 8");
    }

    @Test
    void rejectsPasswordWithoutDigit() {
        assertThatThrownBy(() -> policy.validate("OnlyLetters")).hasMessageContaining("letter and one digit");
    }

    @Test
    void rejectsPasswordWithoutLetter() {
        assertThatThrownBy(() -> policy.validate("12345678")).hasMessageContaining("letter and one digit");
    }

    @Test
    void bcryptHashingProducesDistinctSecureSalts() {
        String raw = "SwiftDrop123!";
        String hash1 = encoder.encode(raw);
        String hash2 = encoder.encode(raw);

        // Hash differs from cleartext input
        assertThat(hash1).isNotEqualTo(raw);
        assertThat(hash2).isNotEqualTo(raw);

        // BCrypt uses unique random salt per invocation, so two hashes of the same password differ
        assertThat(hash1).isNotEqualTo(hash2);

        // Both verify correctly against the raw password
        assertThat(encoder.matches(raw, hash1)).isTrue();
        assertThat(encoder.matches(raw, hash2)).isTrue();
        assertThat(encoder.matches("WrongPassword!", hash1)).isFalse();
    }
}
