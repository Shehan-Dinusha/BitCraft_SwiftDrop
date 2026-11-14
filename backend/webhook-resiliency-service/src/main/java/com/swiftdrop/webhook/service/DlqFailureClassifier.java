package com.swiftdrop.webhook.service;

import com.swiftdrop.webhook.domain.entity.AttemptErrorClass;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Classifications and sanitised summaries for failed delivery attempts and dead-letter records.
 *
 * <p>Two kinds of stored failure text exist:</p>
 * <ul>
 *   <li><b>Sanitised reasons</b> written by the current dispatcher via {@link #storedReason}: the error class name,
 *       optionally followed by {@code " HTTP <status>"} (for example {@code "HTTP_SERVER_ERROR HTTP 503"}).
 *       They are parsed exactly.</li>
 *   <li><b>Legacy raw exception messages</b>, which routinely contain the merchant's callback URL, host name,
 *       IP address and port (for example {@code "503 Service Unavailable from POST https://shop.example/hook"}
 *       or {@code "Connection refused: shop.example/10.0.0.5:443"}). They are classified heuristically;
 *       anything not recognised is {@link AttemptErrorClass#UNKNOWN}.</li>
 * </ul>
 *
 * <p>Every summary produced here is chosen from a fixed set of sentences; the only value ever taken from stored
 * text is a validated HTTP status code. Summaries are therefore safe to show to callers who must not see
 * merchant infrastructure.</p>
 */
public final class DlqFailureClassifier {

    /** Spring WebClient's response-exception message: {@code "<status> <reason> from <METHOD> <url>"}. */
    private static final Pattern HTTP_STATUS = Pattern.compile("^(\\d{3}) [^\\r\\n]* from [A-Z]+ ");

    /** Format written by {@link #storedReason}. */
    private static final Pattern SANITISED_REASON = Pattern.compile("^([A-Z_]{1,32})(?: HTTP (\\d{3}))?$");

    private DlqFailureClassifier() {
    }

    public record Classification(AttemptErrorClass errorClass, String summary) {
    }

    /** Fixed, infrastructure-free sentence for a classified failure. {@code httpStatus} may be null. */
    public static Classification describe(AttemptErrorClass errorClass, Integer httpStatus) {
        if (errorClass == null) {
            return unknown();
        }
        String status = httpStatus != null ? " (HTTP " + httpStatus + ")" : "";
        return switch (errorClass) {
            case TIMEOUT -> new Classification(errorClass, "Endpoint did not respond before the timeout");
            case CONNECTION_FAILURE -> new Classification(errorClass,
                    "Could not connect to the endpoint (network, DNS or TLS failure)");
            case HTTP_RATE_LIMITED -> new Classification(errorClass, "Endpoint rate-limited the request" + status);
            case HTTP_CLIENT_ERROR -> new Classification(errorClass, "Endpoint rejected the request" + status);
            case HTTP_SERVER_ERROR -> new Classification(errorClass, "Endpoint returned a server error" + status);
            case INVALID_RESPONSE -> new Classification(errorClass,
                    "Endpoint returned a non-2xx, non-error status such as a redirect" + status);
            case BLOCKED_DESTINATION -> new Classification(errorClass, "Destination is not allowed");
            case UNKNOWN -> unknown();
        };
    }

    /** Text persisted as a failure reason: class name plus optional status, never raw exception text. */
    public static String storedReason(AttemptErrorClass errorClass, Integer httpStatus) {
        AttemptErrorClass safeClass = errorClass != null ? errorClass : AttemptErrorClass.UNKNOWN;
        return httpStatus != null ? safeClass.name() + " HTTP " + httpStatus : safeClass.name();
    }

    public static Classification classify(String storedFailureReason) {
        if (storedFailureReason == null || storedFailureReason.isBlank()) {
            return unknown();
        }
        Matcher sanitised = SANITISED_REASON.matcher(storedFailureReason);
        if (sanitised.matches()) {
            AttemptErrorClass errorClass = parseErrorClass(sanitised.group(1));
            if (errorClass != null) {
                Integer status = sanitised.group(2) != null ? Integer.valueOf(sanitised.group(2)) : null;
                return describe(errorClass, status);
            }
        }
        Matcher http = HTTP_STATUS.matcher(storedFailureReason);
        if (http.find()) {
            int status = Integer.parseInt(http.group(1));
            if (status == 429) {
                return describe(AttemptErrorClass.HTTP_RATE_LIMITED, status);
            }
            if (status >= 400 && status < 500) {
                return describe(AttemptErrorClass.HTTP_CLIENT_ERROR, status);
            }
            if (status >= 500 && status < 600) {
                return describe(AttemptErrorClass.HTTP_SERVER_ERROR, status);
            }
            return unknown();
        }
        String text = storedFailureReason.toLowerCase(Locale.ROOT);
        if (text.contains("timeoutexception") || text.contains("did not observe any item or completion")
                || text.contains("timed out") || text.contains("timeout")) {
            return describe(AttemptErrorClass.TIMEOUT, null);
        }
        if (text.contains("connection refused") || text.contains("connection reset") || text.contains("connectexception")
                || text.contains("failed to resolve") || text.contains("unknownhost") || text.contains("no route to host")
                || text.contains("ssl") || text.contains("handshake") || text.contains("connection failed")) {
            return describe(AttemptErrorClass.CONNECTION_FAILURE, null);
        }
        return unknown();
    }

    private static AttemptErrorClass parseErrorClass(String name) {
        try {
            return AttemptErrorClass.valueOf(name);
        } catch (IllegalArgumentException notAClass) {
            return null;
        }
    }

    private static Classification unknown() {
        return new Classification(AttemptErrorClass.UNKNOWN, "Delivery failed for an unclassified reason");
    }
}
