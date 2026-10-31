package com.swiftdrop.webhook.resiliency;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Slf4j
@Component
public class HmacSignatureGenerator {

    private static final String HMAC_SHA256 = "HmacSHA256";

    /**
     * Generates standard HMAC-SHA256 hex signature header: sha256=<hex_digest>
     */
    public String calculateSignature(String payload, String secretKey) {
        if (secretKey == null || secretKey.isEmpty()) {
            throw new IllegalArgumentException("Secret key must not be null or empty");
        }
        if (payload == null) {
            payload = "";
        }

        try {
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return "sha256=" + HexFormat.of().formatHex(hmacBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Failed to calculate HMAC-SHA256 webhook signature", e);
            throw new IllegalStateException("Failed to calculate HMAC signature", e);
        }
    }

    public boolean verifySignature(String payload, String secretKey, String expectedHeader) {
        if (expectedHeader == null || !expectedHeader.startsWith("sha256=")) {
            return false;
        }
        if (secretKey == null || secretKey.isBlank() || payload == null) {
            return false;
        }
        try {
            String actual = calculateSignature(payload, secretKey);
            return actual.equals(expectedHeader);
        } catch (Exception e) {
            return false;
        }
    }
}
