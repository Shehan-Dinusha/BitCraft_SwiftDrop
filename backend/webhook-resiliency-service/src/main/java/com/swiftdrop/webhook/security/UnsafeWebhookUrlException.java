package com.swiftdrop.webhook.security;

/**
 * A webhook target URL violates the destination policy. The message is chosen from a fixed set (see
 * {@link Reason#message()}) and never contains the URL, resolved addresses or resolver error text, so it is safe to
 * return to the caller and to log.
 */
public class UnsafeWebhookUrlException extends RuntimeException {

    public enum Reason {
        MALFORMED("Target URL is not a valid absolute URL"),
        UNSUPPORTED_SCHEME("Target URL must use HTTPS"),
        HTTPS_REQUIRED("Target URL must use HTTPS"),
        USER_INFO("Target URL must not contain a username or password"),
        INVALID_PORT("Target URL has an invalid port"),
        INTERNAL_HOST("Target URL host is an internal or reserved name; use a public hostname"),
        NON_CANONICAL_IP("Target URL host uses an unsupported IP address notation"),
        BLOCKED_ADDRESS("Target URL points to a private, loopback, link-local or otherwise non-public address"),
        UNRESOLVABLE("Target URL host could not be resolved to a public address");

        private final String message;

        Reason(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }
    }

    private final Reason reason;

    public UnsafeWebhookUrlException(Reason reason) {
        super(reason.message());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
