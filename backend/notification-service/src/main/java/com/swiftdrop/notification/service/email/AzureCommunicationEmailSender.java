package com.swiftdrop.notification.service.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.notification.util.PiiMasker;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Sends email through Azure Communication Services using its REST API and HMAC-SHA256
 * access-key authentication. Only HTTP 202 (accepted for delivery) counts as success.
 *
 * <p>The access key, recipient address and provider response bodies are never logged or
 * placed in exception messages, because exception messages are stored in dispatch logs.
 */
@Slf4j
public class AzureCommunicationEmailSender implements EmailSender {

    static final String SEND_PATH = "/emails:send?api-version=2023-03-31";
    private static final DateTimeFormatter RFC_1123 = DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneOffset.UTC);

    private final URI sendUri;
    private final String signedHost;
    private final byte[] accessKey;
    private final String senderAddress;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Duration timeout;

    public AzureCommunicationEmailSender(String endpoint, String base64AccessKey, String senderAddress,
                                         ObjectMapper objectMapper) {
        this(endpoint, base64AccessKey, senderAddress, objectMapper,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                Clock.systemUTC(), Duration.ofSeconds(10));
    }

    AzureCommunicationEmailSender(String endpoint, String base64AccessKey, String senderAddress,
                                  ObjectMapper objectMapper, HttpClient httpClient, Clock clock, Duration timeout) {
        URI base = URI.create(endpoint.replaceAll("/+$", ""));
        this.sendUri = URI.create(base + SEND_PATH);
        this.signedHost = base.getPort() == -1 ? base.getHost() : base.getHost() + ":" + base.getPort();
        this.accessKey = Base64.getDecoder().decode(base64AccessKey);
        this.senderAddress = senderAddress;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.clock = clock;
        this.timeout = timeout;
    }

    @Override
    public void send(String to, String subject, String plainTextBody) {
        byte[] body = buildBody(to, subject, plainTextBody);
        String date = RFC_1123.format(clock.instant());
        String contentHash = base64(sha256(body));
        String signature = base64(hmacSha256("POST\n" + SEND_PATH + "\n" + date + ";" + signedHost + ";" + contentHash));

        HttpRequest request = HttpRequest.newBuilder(sendUri)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("x-ms-date", date)
                .header("x-ms-content-sha256", contentHash)
                .header("Authorization", "HMAC-SHA256 SignedHeaders=x-ms-date;host;x-ms-content-sha256&Signature=" + signature)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpResponse<Void> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (IOException e) {
            throw new EmailDeliveryException("Azure email service unreachable");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EmailDeliveryException("Azure email request interrupted");
        }

        if (response.statusCode() != 202) {
            throw new EmailDeliveryException("Azure email rejected the message (HTTP " + response.statusCode() + ")");
        }
        log.info("Azure accepted email for {} (operation {})", PiiMasker.maskEmail(to), operationId(response));
    }

    /** ACS returns {@code Operation-Location: https://.../emails/operations/<id>?api-version=...}. */
    static String operationId(HttpResponse<?> response) {
        return response.headers().firstValue("Operation-Location")
                .map(location -> location.replaceAll("\\?.*$", ""))
                .map(path -> path.substring(path.lastIndexOf('/') + 1))
                .filter(id -> !id.isBlank())
                .orElse("unknown");
    }

    @Override
    public String providerName() {
        return "azure";
    }

    private byte[] buildBody(String to, String subject, String plainTextBody) {
        Map<String, Object> payload = Map.of(
                "senderAddress", senderAddress,
                "content", Map.of(
                        "subject", subject == null || subject.isBlank() ? "SwiftDrop notification" : subject,
                        "plainText", plainTextBody == null ? "" : plainTextBody),
                "recipients", Map.of("to", List.of(Map.of("address", to))));
        try {
            return objectMapper.writeValueAsBytes(payload);
        } catch (IOException e) {
            throw new EmailDeliveryException("Could not build the email request");
        }
    }

    private byte[] hmacSha256(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(accessKey, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is unavailable", e);
        }
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static String base64(byte[] value) {
        return Base64.getEncoder().encodeToString(value);
    }
}
