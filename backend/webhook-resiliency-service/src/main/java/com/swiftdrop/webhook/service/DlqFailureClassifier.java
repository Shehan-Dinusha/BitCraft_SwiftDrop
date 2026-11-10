package com.swiftdrop.webhook.service;

import com.swiftdrop.webhook.domain.entity.AttemptErrorClass;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Derives a classification and a sanitised summary from a legacy dead-letter {@code failure_reason}.
 *
 * <p>The legacy dispatcher stored raw exception messages, which routinely contain the merchant's callback URL,
 * host name, IP address and port (for example {@code "503 Service Unavailable from POST https://shop.example/hook"}
 * or {@code "Connection refused: shop.example/10.0.0.5:443"}). The summary produced here is chosen from a fixed
 * set of sentences; the only value ever taken from the raw text is a validated HTTP status code. It is therefore
 * safe to show to callers who must not see merchant infrastructure.</p>
 *
 * <p>Classification of legacy text is heuristic: anything not recognised is {@link AttemptErrorClass#UNKNOWN}.</p>
 */
public final class DlqFailureClassifier {

    /** Spring WebClient's response-exception message: {@code "<status> <reason> from <METHOD> <url>"}. */
    private static final Pattern HTTP_STATUS = Pattern.compile("^(\\d{3}) [^\\r\\n]* from [A-Z]+ ");

    private DlqFailureClassifier() {
    }

    public record Classification(AttemptErrorClass errorClass, String summary) {
    }

    public static Classification classify(String rawFailureReason) {
        if (rawFailureReason == null || rawFailureReason.isBlank()) {
            return unknown();
        }
        Matcher http = HTTP_STATUS.matcher(rawFailureReason);
        if (http.find()) {
            int status = Integer.parseInt(http.group(1));
            if (status == 429) {
                return new Classification(AttemptErrorClass.HTTP_RATE_LIMITED, "Endpoint rate-limited the request (HTTP 429)");
            }
            if (status >= 400 && status < 500) {
                return new Classification(AttemptErrorClass.HTTP_CLIENT_ERROR, "Endpoint rejected the request (HTTP " + status + ")");
            }
            if (status >= 500 && status < 600) {
                return new Classification(AttemptErrorClass.HTTP_SERVER_ERROR, "Endpoint returned a server error (HTTP " + status + ")");
            }
            return unknown();
        }
        String text = rawFailureReason.toLowerCase(Locale.ROOT);
        if (text.contains("timeoutexception") || text.contains("did not observe any item or completion")
                || text.contains("timed out") || text.contains("timeout")) {
            return new Classification(AttemptErrorClass.TIMEOUT, "Endpoint did not respond before the timeout");
        }
        if (text.contains("connection refused") || text.contains("connection reset") || text.contains("connectexception")
                || text.contains("failed to resolve") || text.contains("unknownhost") || text.contains("no route to host")
                || text.contains("ssl") || text.contains("handshake") || text.contains("connection failed")) {
            return new Classification(AttemptErrorClass.CONNECTION_FAILURE,
                    "Could not connect to the endpoint (network, DNS or TLS failure)");
        }
        return unknown();
    }

    private static Classification unknown() {
        return new Classification(AttemptErrorClass.UNKNOWN, "Delivery failed for an unclassified reason");
    }
}
