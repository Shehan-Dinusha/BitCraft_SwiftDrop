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
     * e.g. {@code 0771235678 -> 07X***5678} or {@code +94771235678 -> 07X***5678}.
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return "";
        }
        String digits = phone.replaceAll("\\D", "");
        // International form (+94 then nine digits) is the same number as 0 then nine digits.
        if (digits.length() == 11 && digits.startsWith("94")) {
            digits = "0" + digits.substring(2);
        }
        if (digits.length() == 10) {
            return digits.substring(0, 2) + "X***" + digits.substring(6);
        }
        if (digits.length() <= 4) {
            return "***";
        }
        if (digits.length() <= 7) {
            return digits.substring(0, 2) + "***" + digits.substring(digits.length() - 2);
        }
        return digits.substring(0, 3) + "***" + digits.substring(digits.length() - 4);
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
        if ("UNRESOLVED".equalsIgnoreCase(trimmed)) {
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
