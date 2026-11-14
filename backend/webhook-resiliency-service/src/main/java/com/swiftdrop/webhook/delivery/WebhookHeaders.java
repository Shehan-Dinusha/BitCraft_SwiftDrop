package com.swiftdrop.webhook.delivery;

/**
 * Header names of an outbound merchant webhook request (decision D5) and the signature format constants (D4).
 * The sender (Step 7) assembles these; this class only fixes the names so every component uses the same ones.
 */
public final class WebhookHeaders {

    /** {@code sha256=<64 lowercase hex chars>}: HMAC-SHA256 over {@code "<timestamp>." + raw body}. */
    public static final String SIGNATURE = "X-SwiftDrop-Signature";
    /** Unix time in whole seconds, the same value that was signed. */
    public static final String TIMESTAMP = "X-SwiftDrop-Timestamp";
    /** Inbound event id; identical for every attempt and replay of the event. Merchants de-duplicate on it. */
    public static final String EVENT_ID = "X-SwiftDrop-Event-Id";
    /** Delivery job id; stable across all retries of the same job (a replay is a new job with a new id). */
    public static final String DELIVERY_ID = "X-SwiftDrop-Delivery-Id";
    /** Event type, e.g. {@code parcel.status.changed}. */
    public static final String EVENT = "X-SwiftDrop-Event";
    /** 1-based attempt number of this delivery job. */
    public static final String ATTEMPT = "X-SwiftDrop-Attempt";
    public static final String USER_AGENT = "User-Agent";

    public static final String USER_AGENT_VALUE = "SwiftDrop-Webhooks/1";
    public static final String SIGNATURE_PREFIX = "sha256=";

    private WebhookHeaders() {
    }
}
