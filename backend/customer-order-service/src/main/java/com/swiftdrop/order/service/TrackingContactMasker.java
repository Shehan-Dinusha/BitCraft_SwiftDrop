package com.swiftdrop.order.service;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Masks the contact details shown on the public tracking page. Masking happens here so the raw
 * name and phone never leave this service.
 */
public final class TrackingContactMasker {

    static final String UNKNOWN_PHONE = "***";

    private TrackingContactMasker() {
    }

    /** "Nimal Perera" becomes "N. P."; a blank or missing name becomes null. */
    public static String maskName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return Arrays.stream(name.trim().split("\\s+"))
                .map(token -> new String(Character.toChars(token.codePointAt(0))).toUpperCase(Locale.ROOT) + ".")
                .collect(Collectors.joining(" "));
    }

    /**
     * "0771234567" and "+94771234567" both become "07X***4567". Anything that is not a full
     * ten-digit Sri Lankan number becomes "***".
     */
    public static String maskPhone(String phone) {
        if (phone == null) {
            return UNKNOWN_PHONE;
        }
        String digits = phone.replaceAll("\\D", "");
        // International form (+94 then nine digits) is the same number as 0 then nine digits.
        if (digits.length() == 11 && digits.startsWith("94")) {
            digits = "0" + digits.substring(2);
        }
        if (digits.length() != 10) {
            return UNKNOWN_PHONE;
        }
        return digits.substring(0, 2) + "X***" + digits.substring(6);
    }
}
