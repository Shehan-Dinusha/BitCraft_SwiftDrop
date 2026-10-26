package com.swiftdrop.iam.service;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Central password policy. BCrypt only considers the first 72 bytes, so the
 * upper bound is deliberate.
 */
@Component
public class PasswordPolicy {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 72;
    private static final Pattern LETTER = Pattern.compile("[A-Za-z]");
    private static final Pattern DIGIT = Pattern.compile("\\d");

    public void validate(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }
        if (password.length() < MIN_LENGTH) {
            throw new IllegalArgumentException("Password must be at least " + MIN_LENGTH + " characters");
        }
        if (password.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Password must not exceed " + MAX_LENGTH + " characters");
        }
        if (!LETTER.matcher(password).find() || !DIGIT.matcher(password).find()) {
            throw new IllegalArgumentException("Password must contain at least one letter and one digit");
        }
    }
}
