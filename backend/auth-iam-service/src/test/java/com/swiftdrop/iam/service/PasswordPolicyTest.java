package com.swiftdrop.iam.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

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
}
