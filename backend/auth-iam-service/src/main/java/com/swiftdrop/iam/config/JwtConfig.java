package com.swiftdrop.iam.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtConfig {

    @Value("${swiftdrop.jwt.secret:swiftdrop-local-dev-secret-key-must-be-at-least-256-bits-long!!}")
    private String secret;

    public String getSecret() {
        return secret;
    }
}
