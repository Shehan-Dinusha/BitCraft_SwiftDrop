package com.swiftdrop.notification.service.push;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;

import static com.swiftdrop.notification.service.push.EcKeys.decodeBase64Url;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebPushEncryptionTest {

    // RFC 8291, Section 5 and Appendix A.
    private static final String AS_PUBLIC = "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
    private static final String AS_PRIVATE = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw";
    private static final String UA_PUBLIC = "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    private static final String UA_PRIVATE = "q1dXpw3UpT5VOmu_cf_v6ih07Aems3njxI-JWgLcM94";
    private static final String SALT = "DGv6ra1nlYgDCS1FRnbzlw";
    private static final String AUTH = "BTBZMqHH6r4Tts7J_aSIgg";
    private static final String HEADER = "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
    private static final String CIPHERTEXT = "8pfeW0KbunFT06SuDKoJH9Ql87S1QUrdirN6GcG7sFz1y1sqLgVi1VhjVkHsUoEsbI_0LpXMuGvnzQ";
    private static final String PLAINTEXT = "When I grow up, I want to be a watermelon";

    @Test
    @DisplayName("Matches the RFC 8291 worked example byte for byte")
    void rfc8291Example() {
        KeyPair asKeys = new KeyPair(EcKeys.publicKey(decodeBase64Url(AS_PUBLIC)), EcKeys.privateKey(decodeBase64Url(AS_PRIVATE)));

        byte[] body = WebPushEncryption.encrypt(PLAINTEXT.getBytes(StandardCharsets.US_ASCII),
                decodeBase64Url(UA_PUBLIC), decodeBase64Url(AUTH), asKeys, decodeBase64Url(SALT));

        ByteArrayOutputStream expected = new ByteArrayOutputStream();
        expected.writeBytes(decodeBase64Url(HEADER));
        expected.writeBytes(decodeBase64Url(CIPHERTEXT));
        assertThat(body).isEqualTo(expected.toByteArray());
    }

    @Test
    @DisplayName("A random-key message decrypts on the receiving side, and no two messages are alike")
    void roundTrip() throws Exception {
        byte[] message = "{\"title\":\"Delivered\"}".getBytes(StandardCharsets.UTF_8);
        byte[] uaPublic = decodeBase64Url(UA_PUBLIC);
        byte[] auth = decodeBase64Url(AUTH);

        byte[] first = WebPushEncryption.encrypt(message, uaPublic, auth);
        byte[] second = WebPushEncryption.encrypt(message, uaPublic, auth);

        assertThat(first).isNotEqualTo(second);
        assertThat(decrypt(first, decodeBase64Url(UA_PRIVATE), uaPublic, auth)).isEqualTo(message);
    }

    @Test
    void rejectsBadSubscriptionKeys() {
        byte[] message = "x".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> WebPushEncryption.encrypt(message, new byte[65], decodeBase64Url(AUTH)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WebPushEncryption.encrypt(message, decodeBase64Url(UA_PUBLIC), new byte[8]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** The browser's side of RFC 8291, used to prove our output is readable. */
    private static byte[] decrypt(byte[] body, byte[] uaPrivate, byte[] uaPublic, byte[] auth) throws Exception {
        byte[] salt = Arrays.copyOfRange(body, 0, 16);
        int idLength = body[20] & 0xFF;
        byte[] asPublic = Arrays.copyOfRange(body, 21, 21 + idLength);
        byte[] ciphertext = Arrays.copyOfRange(body, 21 + idLength, body.length);

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(EcKeys.privateKey(uaPrivate));
        agreement.doPhase((ECPublicKey) EcKeys.publicKey(asPublic), true);
        byte[] ecdh = agreement.generateSecret();

        ByteArrayOutputStream keyInfo = new ByteArrayOutputStream();
        keyInfo.writeBytes("WebPush: info".getBytes(StandardCharsets.US_ASCII));
        keyInfo.write(0);
        keyInfo.writeBytes(uaPublic);
        keyInfo.writeBytes(asPublic);
        byte[] ikm = WebPushEncryption.hkdf(auth, ecdh, keyInfo.toByteArray(), 32);
        byte[] cek = WebPushEncryption.hkdf(salt, ikm, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
        byte[] nonce = WebPushEncryption.hkdf(salt, ikm, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] padded = cipher.doFinal(ciphertext);
        assertThat(padded[padded.length - 1]).isEqualTo((byte) 2);
        return Arrays.copyOf(padded, padded.length - 1);
    }
}
