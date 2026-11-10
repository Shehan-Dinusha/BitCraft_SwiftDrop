package com.swiftdrop.iam.config;

import com.swiftdrop.iam.dto.JwkResponse;
import com.swiftdrop.iam.dto.JwksResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/** Immutable signing material loaded once; no private or symmetric key is ever exposed in JWKS. */
@Slf4j
@Component
public class JwtKeyProvider {
    private final boolean rsaEnabled;
    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;
    private final String keyId;

    public JwtKeyProvider(JwtConfig config, Environment environment) {
        if (!"HS256".equals(config.getAlgorithm()) && !"RS256".equals(config.getAlgorithm())) {
            throw new IllegalStateException("JWT_ALGORITHM must be HS256 or RS256");
        }
        rsaEnabled = "RS256".equals(config.getAlgorithm());
        boolean hasPrivate = config.getPrivateKey() != null && !config.getPrivateKey().isBlank();
        boolean hasPublic = config.getPublicKey() != null && !config.getPublicKey().isBlank();
        if (hasPrivate != hasPublic) throw new IllegalStateException("Both JWT_PRIVATE_KEY and JWT_PUBLIC_KEY are required");

        try {
            KeyPair pair;
            if (hasPrivate) {
                KeyFactory factory = KeyFactory.getInstance("RSA");
                pair = new KeyPair(factory.generatePublic(new X509EncodedKeySpec(decodePem(config.getPublicKey(), "PUBLIC KEY"))),
                        factory.generatePrivate(new PKCS8EncodedKeySpec(decodePem(config.getPrivateKey(), "PRIVATE KEY"))));
            } else if (rsaEnabled) {
                // An aks/production profile must never get an ephemeral key even if dev is also active.
                boolean devOnly = environment.acceptsProfiles(Profiles.of("dev"))
                        && Arrays.stream(environment.getActiveProfiles()).allMatch("dev"::equals);
                if (!devOnly) throw new IllegalStateException("RS256 requires configured RSA keys outside the explicit dev profile");
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(2048);
                pair = generator.generateKeyPair();
                log.warn("Using an ephemeral development RSA key; restarting invalidates existing RSA access tokens");
            } else {
                privateKey = null;
                publicKey = null;
                keyId = null;
                return;
            }
            privateKey = (RSAPrivateKey) pair.getPrivate();
            publicKey = (RSAPublicKey) pair.getPublic();
            if (privateKey.getModulus().bitLength() < 2048 || publicKey.getModulus().bitLength() < 2048) {
                throw new IllegalStateException("RSA keys must have at least 2048 bits");
            }
            Signature proof = Signature.getInstance("SHA256withRSA");
            byte[] message = "SwiftDrop signing key validation".getBytes(StandardCharsets.US_ASCII);
            proof.initSign(privateKey);
            proof.update(message);
            byte[] signature = proof.sign();
            proof.initVerify(publicKey);
            proof.update(message);
            if (!proof.verify(signature)) throw new IllegalStateException("Configured RSA keys do not form a matching pair");
            keyId = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(publicKey.getEncoded()));
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            // Do not include parser causes: malformed key material must never reach logs or responses.
            throw new IllegalStateException("Unable to load RSA keys; use matching PKCS#8 private and X.509 public PEM keys");
        }
    }

    public boolean isRsaEnabled() { return rsaEnabled; }
    public RSAPrivateKey signingKey() { return privateKey; }
    public RSAPublicKey verificationKey() { return publicKey; }
    public String keyId() { return keyId; }

    public JwksResponse jwks() {
        return new JwksResponse(publicKey == null ? List.of() : List.of(
                new JwkResponse("RSA", "sig", "RS256", keyId,
                        unsigned(publicKey.getModulus()), unsigned(publicKey.getPublicExponent()))));
    }

    private static byte[] decodePem(String input, String label) {
        String pem = input.replace("\\n", "\n").trim();
        String begin = "-----BEGIN " + label + "-----";
        String end = "-----END " + label + "-----";
        if (!pem.startsWith(begin) || !pem.endsWith(end)) throw new IllegalArgumentException("Unsupported PEM encoding");
        String base64 = pem.substring(begin.length(), pem.length() - end.length()).replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64);
    }

    private static String unsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
