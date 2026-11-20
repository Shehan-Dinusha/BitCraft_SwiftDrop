package com.swiftdrop.notification.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/** Small formatting helpers for notification text shown to people. */
public final class DisplayText {

    private static final ZoneId SRI_LANKA = ZoneId.of("Asia/Colombo");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH);

    private DisplayText() {
    }

    /** {@code NUWARA_ELIYA} -> {@code Nuwara Eliya}; null or blank -> null. */
    public static String title(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        for (String word : value.trim().replace('_', ' ').toLowerCase(Locale.ROOT).split("\\s+")) {
            if (!word.isEmpty()) {
                out.append(out.isEmpty() ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return out.toString();
    }

    /** ISO instant -> "19 Nov 2026, 9:26 p.m." in Sri Lanka time; other text is returned unchanged. */
    public static String when(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return WHEN.format(Instant.parse(value.trim()).atZone(SRI_LANKA)).replace("AM", "a.m.").replace("PM", "p.m.");
        } catch (DateTimeParseException e) {
            return value.trim();
        }
    }

    /**
     * A delivery failure code as a short phrase that reads after "We could not deliver parcel X: ".
     * {@code RECIPIENT_UNAVAILABLE} -> "the recipient was not available"; unknown codes become plain words.
     */
    public static String failureReason(String code) {
        if (code == null || code.isBlank()) {
            return "the delivery attempt was not successful";
        }
        return switch (code.trim().toUpperCase(Locale.ROOT)) {
            case "CUSTOMER_UNAVAILABLE", "RECIPIENT_UNAVAILABLE" -> "the recipient was not available";
            case "WRONG_ADDRESS", "INCORRECT_ADDRESS" -> "the delivery address could not be found";
            case "REFUSED_BY_CUSTOMER", "RECIPIENT_REJECTED" -> "the recipient refused the parcel";
            case "ACCESS_RESTRICTED" -> "the courier could not reach the address";
            case "PAYMENT_FAILED" -> "the cash on delivery payment could not be collected";
            case "DAMAGED", "DAMAGED_IN_TRANSIT" -> "the parcel was damaged";
            case "WEATHER_DELAY", "ADVERSE_WEATHER" -> "bad weather prevented the delivery";
            case "OTHER" -> "the delivery attempt was not successful";
            default -> {
                String words = code.trim().replace('_', ' ').toLowerCase(Locale.ROOT);
                // Free text from older producers is kept as written.
                yield code.contains("_") || code.equals(code.toUpperCase(Locale.ROOT)) ? words : code.trim();
            }
        };
    }

    /** "Chrome on Windows" from a browser user-agent string; "an unknown device" when it cannot tell. */
    public static String device(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "an unknown device";
        }
        String ua = userAgent.toLowerCase(Locale.ROOT);
        String browser = ua.contains("edg/") ? "Microsoft Edge"
                : ua.contains("opr/") || ua.contains("opera") ? "Opera"
                : ua.contains("firefox/") ? "Firefox"
                : ua.contains("chrome/") || ua.contains("crios/") ? "Chrome"
                : ua.contains("safari/") ? "Safari"
                : null;
        String os = ua.contains("windows") ? "Windows"
                : ua.contains("android") ? "Android"
                : ua.contains("iphone") || ua.contains("ipad") ? "iOS"
                : ua.contains("mac os x") || ua.contains("macintosh") ? "macOS"
                : ua.contains("linux") ? "Linux"
                : null;
        if (browser == null && os == null) {
            return "an unknown device";
        }
        if (browser == null) {
            return "a browser on " + os;
        }
        return os == null ? browser : browser + " on " + os;
    }

    /** Partly masked IP: {@code 203.94.12.7} -> {@code 203.94.x.x}; IPv6 keeps the first two groups. */
    public static String maskIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        String value = ip.trim();
        String[] v4 = value.split("\\.");
        if (v4.length == 4) {
            return v4[0] + "." + v4[1] + ".x.x";
        }
        String[] v6 = value.split(":");
        if (v6.length > 2) {
            return v6[0] + ":" + v6[1] + ":…";
        }
        return null;
    }
}
