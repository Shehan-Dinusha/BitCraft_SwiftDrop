package com.swiftdrop.iam.support;

import com.swiftdrop.iam.config.JwtConfig;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

/** Synthetic keys generated only in tests; never deployment credentials. */
public final class RsaTestKeys {
    public static final KeyPair PRIMARY = generate(2048);
    public static final KeyPair OTHER = generate(2048);

    private RsaTestKeys() { }

    public static KeyPair generate(int bits) {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(bits);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Test RSA generation unavailable", exception);
        }
    }

    public static JwtConfig config(KeyPair pair) {
        var config = new JwtConfig();
        config.setAlgorithm("RS256");
        config.setPrivateKey(pem("PRIVATE KEY", pair.getPrivate().getEncoded()));
        config.setPublicKey(pem("PUBLIC KEY", pair.getPublic().getEncoded()));
        return config;
    }

    public static String pem(String label, byte[] encoded) {
        return "-----BEGIN " + label + "-----\n" + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encoded)
                + "\n-----END " + label + "-----";
    }
}
