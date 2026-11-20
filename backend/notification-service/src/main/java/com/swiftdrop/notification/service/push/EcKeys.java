package com.swiftdrop.notification.service.push;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * P-256 (secp256r1) keys in the raw forms Web Push uses: a public key is the 65-byte uncompressed
 * point (0x04 || X || Y) and a private key is the 32-byte scalar, both base64url without padding.
 * JDK only, no third-party crypto library.
 */
public final class EcKeys {

    private static final ECParameterSpec P256 = p256();

    private EcKeys() {
    }

    public static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("P-256 is not available in this JVM", e);
        }
    }

    public static ECPublicKey publicKey(byte[] uncompressed) {
        if (uncompressed == null || uncompressed.length != 65 || uncompressed[0] != 0x04) {
            throw new IllegalArgumentException("A P-256 public key must be 65 bytes starting with 0x04");
        }
        BigInteger x = new BigInteger(1, Arrays.copyOfRange(uncompressed, 1, 33));
        BigInteger y = new BigInteger(1, Arrays.copyOfRange(uncompressed, 33, 65));
        try {
            return (ECPublicKey) KeyFactory.getInstance("EC")
                    .generatePublic(new ECPublicKeySpec(new ECPoint(x, y), P256));
        } catch (GeneralSecurityException e) {
            // Includes points that are not on the curve.
            throw new IllegalArgumentException("Not a valid P-256 public key", e);
        }
    }

    public static ECPrivateKey privateKey(byte[] scalar) {
        if (scalar == null || scalar.length != 32) {
            throw new IllegalArgumentException("A P-256 private key must be 32 bytes");
        }
        try {
            return (ECPrivateKey) KeyFactory.getInstance("EC")
                    .generatePrivate(new ECPrivateKeySpec(new BigInteger(1, scalar), P256));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Not a valid P-256 private key", e);
        }
    }

    /** 0x04 || X || Y, each coordinate left-padded to 32 bytes. */
    public static byte[] encode(ECPublicKey key) {
        byte[] out = new byte[65];
        out[0] = 0x04;
        copyFixed(key.getW().getAffineX(), out, 1);
        copyFixed(key.getW().getAffineY(), out, 33);
        return out;
    }

    public static byte[] encode(ECPrivateKey key) {
        byte[] out = new byte[32];
        copyFixed(key.getS(), out, 0);
        return out;
    }

    public static byte[] decodeBase64Url(String value) {
        return Base64.getUrlDecoder().decode(value.trim());
    }

    public static String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static void copyFixed(BigInteger value, byte[] out, int offset) {
        byte[] bytes = value.toByteArray();
        int start = Math.max(0, bytes.length - 32);
        int length = bytes.length - start;
        System.arraycopy(bytes, start, out, offset + 32 - length, length);
    }

    private static ECParameterSpec p256() {
        return ((ECPublicKey) generate().getPublic()).getParams();
    }
}
