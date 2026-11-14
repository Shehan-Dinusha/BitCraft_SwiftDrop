package com.swiftdrop.webhook.delivery;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Webhook signature contract (decision D4).
 *
 * <pre>
 *   signed bytes = ASCII("&lt;timestamp&gt;.") || raw HTTP body bytes
 *   signature    = "sha256=" + lowercase hex( HMAC-SHA256( key = UTF-8(secret), signed bytes ) )
 * </pre>
 *
 * <ul>
 *   <li>{@code timestamp}: Unix time in whole seconds, sent in {@link WebhookHeaders#TIMESTAMP}.</li>
 *   <li>Key: the UTF-8 bytes of the endpoint secret exactly as stored and as shown once at creation, including its
 *       {@code whsec_} prefix. This is the same key derivation the existing signer has always used, so stored secrets
 *       keep working unchanged.</li>
 *   <li>Body: the exact bytes sent (from {@link WebhookPayloadFactory#buildBytes}). Never re-serialised here.</li>
 * </ul>
 *
 * <p>{@link #verify} is a reference implementation of the merchant-side check (this service sends webhooks, it does
 * not receive them): it backs the documentation and manual verification, and is not exposed through any API.</p>
 *
 * <p>Nothing here logs: secrets, signatures, payloads and signed bytes must never reach logs.</p>
 */
@Component
public class WebhookSigner {

    /**
     * Approved merchant verification window: a request is accepted only if its timestamp is within 5 minutes of
     * the verifier's clock, in either direction. The symmetric window is the only clock-skew allowance; no extra
     * margin is added on top.
     */
    public static final Duration TOLERANCE = Duration.ofMinutes(5);

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final Pattern SIGNATURE_FORMAT = Pattern.compile("^sha256=[0-9a-f]{64}$");
    /** Unsigned decimal seconds; 1–12 digits keeps the value far from overflow and rejects signs, spaces, decimals. */
    private static final Pattern TIMESTAMP_FORMAT = Pattern.compile("^[0-9]{1,12}$");

    private final Clock clock;

    public WebhookSigner() {
        this(Clock.systemUTC());
    }

    public WebhookSigner(Clock clock) {
        this.clock = clock;
    }

    /** Current Unix time in seconds, from this signer's clock: the value to sign and send. */
    public long currentTimestampSeconds() {
        return clock.instant().getEpochSecond();
    }

    /**
     * Signs exactly {@code "<timestampSeconds>." + body}. The body array is only read. Deterministic: the same
     * secret, timestamp and bytes always give the same value.
     *
     * @return {@code sha256=} followed by 64 lowercase hex characters
     */
    public String sign(String secret, long timestampSeconds, byte[] body) {
        if (secret == null || secret.isEmpty()) {
            throw new IllegalArgumentException("Signing secret must not be empty");
        }
        if (timestampSeconds < 0) {
            throw new IllegalArgumentException("Timestamp must not be negative");
        }
        if (body == null) {
            throw new IllegalArgumentException("Body must not be null");
        }
        return WebhookHeaders.SIGNATURE_PREFIX + HexFormat.of().formatHex(hmac(secret, timestampSeconds, body));
    }

    /**
     * Merchant-side check. Returns {@code true} only if the timestamp header is well formed and within
     * {@link #TOLERANCE} of now, and the signature header is a well-formed {@code sha256=} value equal to the one
     * recomputed over the exact body bytes. Every failure returns the same {@code false} (no reason), so a caller
     * cannot learn which part was wrong. The comparison is constant-time ({@link MessageDigest#isEqual}).
     */
    public boolean verify(String secret, String timestampHeader, String signatureHeader, byte[] body) {
        if (secret == null || secret.isEmpty() || body == null
                || timestampHeader == null || !TIMESTAMP_FORMAT.matcher(timestampHeader).matches()
                || signatureHeader == null || !SIGNATURE_FORMAT.matcher(signatureHeader).matches()) {
            return false;
        }
        long timestamp = Long.parseLong(timestampHeader);
        long ageSeconds = currentTimestampSeconds() - timestamp;
        if (Math.abs(ageSeconds) > TOLERANCE.toSeconds()) {
            return false;
        }
        byte[] expected = sign(secret, timestamp, body).getBytes(StandardCharsets.US_ASCII);
        byte[] supplied = signatureHeader.getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, supplied);
    }

    private static byte[] hmac(String secret, long timestampSeconds, byte[] body) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            mac.update((timestampSeconds + ".").getBytes(StandardCharsets.US_ASCII));
            mac.update(body);
            return mac.doFinal();
        } catch (GeneralSecurityException unavailable) {
            // HmacSHA256 is mandatory in every Java runtime; no key or data in the message.
            throw new IllegalStateException("HMAC-SHA256 is unavailable");
        }
    }
}
