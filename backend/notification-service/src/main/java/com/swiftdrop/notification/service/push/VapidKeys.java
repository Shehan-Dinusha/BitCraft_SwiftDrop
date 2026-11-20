package com.swiftdrop.notification.service.push;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Duration;
import java.time.Instant;

/**
 * The application server's VAPID identity (RFC 8292): a P-256 key pair and a contact (mailto: or https:)
 * the push services can use. Builds the {@code Authorization: vapid t=..., k=...} header per push service.
 */
public final class VapidKeys {

    /** JWT lifetime; push services accept at most 24 hours. */
    static final Duration TOKEN_LIFETIME = Duration.ofHours(12);

    private final ECPublicKey publicKey;
    private final ECPrivateKey privateKey;
    private final String publicKeyBase64Url;
    private final String subject;

    private VapidKeys(ECPublicKey publicKey, ECPrivateKey privateKey, String subject) {
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        this.publicKeyBase64Url = EcKeys.base64Url(EcKeys.encode(publicKey));
        this.subject = subject;
    }

    /**
     * @throws IllegalArgumentException when a key is malformed, the two keys are not a pair, or the
     *                                  subject is not a mailto: or https: URI
     */
    public static VapidKeys of(String publicKeyBase64Url, String privateKeyBase64Url, String subject) {
        ECPublicKey pub;
        ECPrivateKey priv;
        try {
            pub = EcKeys.publicKey(EcKeys.decodeBase64Url(publicKeyBase64Url));
            priv = EcKeys.privateKey(EcKeys.decodeBase64Url(privateKeyBase64Url));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("WEB_PUSH_PUBLIC_KEY / WEB_PUSH_PRIVATE_KEY are not base64url P-256 VAPID keys", e);
        }
        String contact = subject == null ? "" : subject.trim();
        if (!contact.startsWith("mailto:") && !contact.startsWith("https://")) {
            throw new IllegalArgumentException("WEB_PUSH_SUBJECT must be a mailto: or https: contact");
        }
        VapidKeys keys = new VapidKeys(pub, priv, contact);
        if (!keys.isPair()) {
            throw new IllegalArgumentException("WEB_PUSH_PUBLIC_KEY does not belong to WEB_PUSH_PRIVATE_KEY");
        }
        return keys;
    }

    /** The key browsers pass to pushManager.subscribe as applicationServerKey. */
    public String publicKeyBase64Url() {
        return publicKeyBase64Url;
    }

    /** {@code vapid t=<ES256 JWT>, k=<public key>} for a push service endpoint. */
    public String authorization(String endpoint, Instant now) {
        URI uri = URI.create(endpoint);
        String audience = uri.getScheme() + "://" + uri.getHost() + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
        String header = EcKeys.base64Url("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
        String claims = EcKeys.base64Url(("{\"aud\":\"" + audience + "\",\"exp\":"
                + now.plus(TOKEN_LIFETIME).getEpochSecond() + ",\"sub\":\"" + subject + "\"}").getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + claims;
        return "vapid t=" + signingInput + "." + EcKeys.base64Url(sign(signingInput.getBytes(StandardCharsets.US_ASCII)))
                + ", k=" + publicKeyBase64Url;
    }

    /** ES256 signature in JWS form: the raw 64-byte R || S, not DER. */
    byte[] sign(byte[] data) {
        try {
            Signature signature = Signature.getInstance("SHA256withECDSAinP1363Format");
            signature.initSign(privateKey);
            signature.update(data);
            return signature.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("VAPID signing failed", e);
        }
    }

    boolean verify(byte[] data, byte[] rawSignature) {
        try {
            Signature signature = Signature.getInstance("SHA256withECDSAinP1363Format");
            signature.initVerify(publicKey);
            signature.update(data);
            return signature.verify(rawSignature);
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    private boolean isPair() {
        byte[] probe = "swiftdrop-vapid-check".getBytes(StandardCharsets.US_ASCII);
        return verify(probe, sign(probe));
    }

    @Override
    public String toString() {
        return "VapidKeys[public=" + publicKeyBase64Url + ", subject=" + subject + "]"; // never the private key
    }
}
