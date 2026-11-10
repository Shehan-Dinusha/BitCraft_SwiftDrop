package com.swiftdrop.notification.util;

/**
 * Utility for masking personally identifiable information (PII) such as
 * phone numbers and email addresses before writing to application logs,
 * error messages, or audit trails.
 */
public final class PiiMasker {

    private PiiMasker() {
    }

    /**
     * Masks a phone number while keeping minimal prefix/suffix context for troubleshooting,
     * e.g. {@code 0771234567 -> 077***4567} or {@code +94771234567 -> +94***4567}.
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return "";
        }
        String trimmed = phone.trim();
        if (trimmed.length() <= 4) {
            return "***";
        }
        if (trimmed.length() <= 7) {
            return trimmed.substring(0, 2) + "***" + trimmed.substring(trimmed.length() - 2);
        }
        return trimmed.substring(0, 3) + "***" + trimmed.substring(trimmed.length() - 4);
    }

    /**
     * Masks an email address preserving the first character of the local part and full domain,
     * e.g. {@code alice@gmail.com -> a***@gmail.com}.
     */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "";
        }
        String trimmed = email.trim();
        int atIndex = trimmed.indexOf('@');
        if (atIndex <= 0 || atIndex == trimmed.length() - 1) {
            return "***";
        }
        return trimmed.charAt(0) + "***" + trimmed.substring(atIndex);
    }

    /**
     * Automatically detects destination type (email, phone, or fallback) and masks PII.
     */
    public static String maskDestination(String destination) {
        if (destination == null || destination.isBlank()) {
            return "";
        }
        String trimmed = destination.trim();
        if ("UNRESOLVED".equals(trimmed)) {
            return trimmed;
        }
        if (trimmed.contains("@")) {
            return maskEmail(trimmed);
        }
        if (trimmed.matches(".*\\d{4,}.*") && trimmed.length() <= 20) {
            return maskPhone(trimmed);
        }
        return "***";
    }
}
