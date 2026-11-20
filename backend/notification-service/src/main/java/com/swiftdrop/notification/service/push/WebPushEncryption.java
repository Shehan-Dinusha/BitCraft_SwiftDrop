package com.swiftdrop.notification.service.push;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;

/**
 * Message encryption for Web Push (RFC 8291) with the aes128gcm content coding (RFC 8188), as a
 * single record. Every message uses a fresh application-server key pair and salt, so two pushes of
 * the same text never produce the same bytes.
 */
public final class WebPushEncryption {

    /** Record size advertised in the header; a single record always fits. */
    private static final int RECORD_SIZE = 4096;
    private static final SecureRandom RANDOM = new SecureRandom();

    private WebPushEncryption() {
    }

    /**
     * @param plaintext   the message (our JSON payload)
     * @param uaPublic    the subscription's p256dh key (65-byte uncompressed point)
     * @param authSecret  the subscription's auth secret (16 bytes)
     * @return the request body: header (salt, record size, key id = our public key) followed by the ciphertext
     */
    public static byte[] encrypt(byte[] plaintext, byte[] uaPublic, byte[] authSecret) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return encrypt(plaintext, uaPublic, authSecret, EcKeys.generate(), salt);
    }

    /** Deterministic form for tests (RFC 8291 Appendix A). */
    static byte[] encrypt(byte[] plaintext, byte[] uaPublic, byte[] authSecret, KeyPair asKeys, byte[] salt) {
        if (authSecret == null || authSecret.length != 16) {
            throw new IllegalArgumentException("The auth secret must be 16 bytes");
        }
        if (plaintext.length > RECORD_SIZE - 17 - 86) {
            throw new IllegalArgumentException("Push message is too large");
        }
        try {
            ECPublicKey uaKey = EcKeys.publicKey(uaPublic);
            byte[] asPublic = EcKeys.encode((ECPublicKey) asKeys.getPublic());

            KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
            agreement.init(asKeys.getPrivate());
            agreement.doPhase(uaKey, true);
            byte[] ecdhSecret = agreement.generateSecret();

            // IKM = HKDF(auth_secret, ecdh_secret, "WebPush: info" || 0x00 || ua_public || as_public, 32)
            byte[] keyInfo = concat("WebPush: info".getBytes(StandardCharsets.US_ASCII), new byte[]{0}, uaPublic, asPublic);
            byte[] ikm = hkdf(authSecret, ecdhSecret, keyInfo, 32);

            byte[] cek = hkdf(salt, ikm, info("aes128gcm"), 16);
            byte[] nonce = hkdf(salt, ikm, info("nonce"), 12);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
            // 0x02 marks the last (and only) record.
            byte[] ciphertext = cipher.doFinal(concat(plaintext, new byte[]{2}));

            byte[] header = ByteBuffer.allocate(16 + 4 + 1 + asPublic.length)
                    .put(salt).putInt(RECORD_SIZE).put((byte) asPublic.length).put(asPublic).array();
            return concat(header, ciphertext);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Web Push encryption failed", e);
        }
    }

    private static byte[] info(String coding) {
        return concat(("Content-Encoding: " + coding).getBytes(StandardCharsets.US_ASCII), new byte[]{0});
    }

    /** HKDF-SHA-256 (RFC 5869) for outputs of at most one hash block, which is all Web Push needs. */
    static byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int length) throws GeneralSecurityException {
        byte[] prk = hmac(salt, ikm);
        return Arrays.copyOf(hmac(prk, concat(info, new byte[]{1})), length);
    }

    private static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }
}
