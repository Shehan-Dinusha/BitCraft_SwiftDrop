package com.swiftdrop.contracts.phone;

import java.util.regex.Pattern;

/**
 * The one phone number rule for every service: +94 and 9 digits, 0 and 9 digits, or just the 9 digits.
 * The 9 digits never start with 0. Nothing else is accepted: no spaces, dashes or other country codes.
 * Numbers are stored as +94 and 9 digits, so the same phone written two ways is the same value.
 */
public final class SriLankanPhone {

    /** For a required number. */
    public static final String REGEX = "^(?:\\+94|0)?[1-9][0-9]{8}$";
    /** For an optional number where an empty string means "none". */
    public static final String OPTIONAL_REGEX = "^$|" + REGEX;
    public static final String MESSAGE = "Enter 9 digits, with or without 0 or +94 in front";

    private static final Pattern PATTERN = Pattern.compile(REGEX);
    private static final int SUBSCRIBER_DIGITS = 9;

    private SriLankanPhone() {
    }

    public static boolean isValid(String value) {
        return value != null && PATTERN.matcher(value.trim()).matches();
    }

    /**
     * "0771234567" and "771234567" become "+94771234567".
     *
     * @throws IllegalArgumentException when the number breaks the rule
     */
    public static String toE164(String value) {
        if (!isValid(value)) {
            throw new IllegalArgumentException(MESSAGE);
        }
        String number = value.trim();
        return "+94" + number.substring(number.length() - SUBSCRIBER_DIGITS);
    }

    /** {@link #toE164} for an optional number: null and blank become null. */
    public static String toE164OrNull(String value) {
        return value == null || value.isBlank() ? null : toE164(value);
    }

    /**
     * {@link #toE164} for a number read from the database: one stored before this rule, such as
     * "+94 11 234 5678", is cleaned up, and one that still breaks the rule is returned unchanged.
     */
    public static String storedToE164(String value) {
        if (value == null) {
            return null;
        }
        String compact = value.replaceAll("[\\s()\\-]", "");
        return isValid(compact) ? toE164(compact) : value;
    }
}
