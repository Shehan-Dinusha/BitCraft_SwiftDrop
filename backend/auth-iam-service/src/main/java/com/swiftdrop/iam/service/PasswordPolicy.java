package com.swiftdrop.iam.service;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Central password policy, aligned with NIST SP 800-63B: a minimum length, a simple composition
 * rule, and rejection of passwords that are easy to guess (very common passwords, or ones built from
 * the user's own username or email). BCrypt only considers the first 72 bytes, so the upper bound is
 * deliberate. The frontend mirrors these rules in {@code features/auth/passwordPolicy.ts}.
 */
@Component
public class PasswordPolicy {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 72;
    /** Usernames/email names shorter than this are too generic to forbid as a substring. */
    private static final int MIN_IDENTITY_FRAGMENT = 3;
    private static final Pattern LETTER = Pattern.compile("[A-Za-z]");
    private static final Pattern DIGIT = Pattern.compile("\\d");

    /** Frequently breached passwords that would otherwise pass the composition rule. */
    private static final Set<String> COMMON_PASSWORDS = Set.of(
            "password1", "password12", "password123", "password1234", "passw0rd", "p@ssw0rd", "p@ssword1",
            "qwerty123", "qwerty1234", "qwertyuiop1", "1q2w3e4r", "1q2w3e4r5t", "zaq12wsx", "abc12345",
            "abcd1234", "a1234567", "a12345678", "123456789a", "12345678a", "1234567890a", "letmein1",
            "welcome1", "welcome123", "iloveyou1", "admin123", "admin1234", "administrator1", "changeme1",
            "default1", "test1234", "testing123", "user1234", "login123", "master123", "monkey123",
            "dragon123", "sunshine1", "princess1", "football1", "baseball1", "trustno1", "shadow123",
            "superman1", "starwars1", "computer1", "secret123", "summer2024", "winter2024", "summer2025",
            "winter2025", "summer2026", "winter2026", "colombo123", "srilanka1", "srilanka123");

    /** Rules that need no account context (bootstrap admin, admin-created users). */
    public void validate(String password) {
        validate(password, null, null);
    }

    /**
     * Full policy check.
     *
     * @param username the account's username, or {@code null}
     * @param email    the account's email address, or {@code null}
     * @throws IllegalArgumentException with a user-facing message when the password is not allowed
     */
    public void validate(String password, String username, String email) {
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

        String lower = password.toLowerCase(Locale.ROOT);
        if (COMMON_PASSWORDS.contains(lower)) {
            throw new IllegalArgumentException("This password is too common. Choose a less predictable one");
        }
        if (containsIdentity(lower, username)) {
            throw new IllegalArgumentException("Password must not contain your username");
        }
        if (email != null) {
            int at = email.indexOf('@');
            String localPart = at > 0 ? email.substring(0, at) : email;
            if (containsIdentity(lower, localPart)) {
                throw new IllegalArgumentException("Password must not contain your email address");
            }
        }
    }

    private static boolean containsIdentity(String lowerPassword, String identity) {
        if (identity == null) {
            return false;
        }
        String fragment = identity.trim().toLowerCase(Locale.ROOT);
        return fragment.length() >= MIN_IDENTITY_FRAGMENT && lowerPassword.contains(fragment);
    }
}
