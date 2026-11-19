package com.swiftdrop.notification.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.notification.service.email.AzureCommunicationEmailSender;
import com.swiftdrop.notification.service.email.EmailSender;
import com.swiftdrop.notification.service.email.MockEmailSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.util.Base64;

/**
 * Selects the email provider from {@code notification.email.provider}. The default is {@code mock},
 * so development, tests and CI never send real email. {@code azure} requires all three
 * Azure Communication Services settings and fails fast at startup when one is missing or malformed.
 */
@Slf4j
@Configuration
public class EmailSenderConfig {

    @Bean
    public EmailSender emailSender(
            @Value("${notification.email.provider:mock}") String provider,
            @Value("${notification.email.azure.endpoint:}") String endpoint,
            @Value("${notification.email.azure.access-key:}") String accessKey,
            @Value("${notification.email.azure.sender-address:}") String senderAddress,
            ObjectMapper objectMapper) {
        return create(provider, endpoint, accessKey, senderAddress, objectMapper);
    }

    static EmailSender create(String provider, String endpoint, String accessKey, String senderAddress,
                              ObjectMapper objectMapper) {
        String selected = provider == null ? "mock" : provider.trim().toLowerCase();
        switch (selected) {
            case "", "mock" -> {
                log.info("Email provider: mock (no real email is sent)");
                return new MockEmailSender();
            }
            case "azure" -> {
                validateAzure(endpoint, accessKey, senderAddress);
                log.info("Email provider: azure ({})", URI.create(endpoint.trim()).getHost());
                return new AzureCommunicationEmailSender(endpoint.trim(), accessKey.trim(), senderAddress.trim(), objectMapper);
            }
            default -> throw new IllegalStateException(
                    "Unknown NOTIFICATION_EMAIL_PROVIDER '" + provider + "'. Use 'mock' or 'azure'.");
        }
    }

    private static void validateAzure(String endpoint, String accessKey, String senderAddress) {
        if (endpoint == null || endpoint.isBlank() || !endpoint.trim().startsWith("https://")) {
            throw new IllegalStateException("ACS_EMAIL_ENDPOINT must be the https:// endpoint of the Communication Services resource.");
        }
        if (accessKey == null || accessKey.isBlank()) {
            throw new IllegalStateException("ACS_EMAIL_ACCESS_KEY is required when NOTIFICATION_EMAIL_PROVIDER=azure.");
        }
        try {
            Base64.getDecoder().decode(accessKey.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("ACS_EMAIL_ACCESS_KEY is not a valid Communication Services access key.");
        }
        if (senderAddress == null || !senderAddress.contains("@")) {
            throw new IllegalStateException("ACS_EMAIL_SENDER_ADDRESS must be a verified sender, for example DoNotReply@<domain>.azurecomm.net.");
        }
    }
}
