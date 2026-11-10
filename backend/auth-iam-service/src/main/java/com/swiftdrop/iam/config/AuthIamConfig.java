package com.swiftdrop.iam.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties(GoogleOAuthConfig.class)
public class AuthIamConfig {

    /**
     * Shared {@link RestTemplate} for outbound HTTP calls (Google OAuth token and userinfo
     * endpoints). Using a single shared instance is fine for low-volume OAuth calls.
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
