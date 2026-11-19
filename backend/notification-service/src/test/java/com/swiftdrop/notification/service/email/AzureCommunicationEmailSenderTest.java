package com.swiftdrop.notification.service.email;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the Azure Communication Services email sender against a local stub that
 * independently verifies the HMAC-SHA256 request signature, content hash and JSON body.
 */
@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
class AzureCommunicationEmailSenderTest {

    private static final String SENDER = "DoNotReply@test.azurecomm.net";
    private static final String RECIPIENT = "customer@example.com";
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-11-14T10:15:30Z"), ZoneOffset.UTC);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String accessKey = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
    private final AtomicReference<String> verificationFailure = new AtomicReference<>();
    private final AtomicReference<JsonNode> receivedBody = new AtomicReference<>();

    private HttpServer server;
    private int responseStatus = 202;

    @BeforeEach
    void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            verificationFailure.set(verifySignature(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().toString(),
                    exchange.getRequestHeaders().getFirst("Host"),
                    exchange.getRequestHeaders().getFirst("x-ms-date"),
                    exchange.getRequestHeaders().getFirst("x-ms-content-sha256"),
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    body));
            receivedBody.set(objectMapper.readTree(body));
            exchange.getResponseHeaders().add("Operation-Location",
                    "http://127.0.0.1/emails/operations/op-123?api-version=2023-03-31");
            exchange.sendResponseHeaders(responseStatus, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopStub() {
        server.stop(0);
    }

    private AzureCommunicationEmailSender sender(String endpoint) {
        return new AzureCommunicationEmailSender(endpoint, accessKey, SENDER, objectMapper,
                HttpClient.newHttpClient(), FIXED_CLOCK, Duration.ofSeconds(5));
    }

    private String stubEndpoint() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/";
    }

    @Test
    @DisplayName("Signs the request correctly and sends sender, recipient, subject and body")
    void sendsSignedRequest() {
        sender(stubEndpoint()).send(RECIPIENT, "Parcel delivered", "Your parcel SD-1 was delivered.", "<p>Your parcel SD-1 was delivered.</p>");

        assertThat(verificationFailure.get()).isNull();
        JsonNode body = receivedBody.get();
        assertThat(body.path("senderAddress").asText()).isEqualTo(SENDER);
        assertThat(body.path("recipients").path("to").path(0).path("address").asText()).isEqualTo(RECIPIENT);
        assertThat(body.path("content").path("subject").asText()).isEqualTo("Parcel delivered");
        assertThat(body.path("content").path("plainText").asText()).isEqualTo("Your parcel SD-1 was delivered.");
        assertThat(body.path("content").path("html").asText()).isEqualTo("<p>Your parcel SD-1 was delivered.</p>");
    }

    @Test
    @DisplayName("Logs the Azure operation id from Operation-Location")
    void logsOperationId(org.springframework.boot.test.system.CapturedOutput output) {
        sender(stubEndpoint()).send(RECIPIENT, "Subject", "Body", null);

        assertThat(output.getOut()).contains("operation op-123").doesNotContain(RECIPIENT);
    }

    @Test
    @DisplayName("Uses a default subject when the template has none")
    void defaultsBlankSubject() {
        sender(stubEndpoint()).send(RECIPIENT, " ", "Body", null);

        assertThat(receivedBody.get().path("content").path("subject").asText()).isEqualTo("SwiftDrop notification");
        assertThat(receivedBody.get().path("content").has("html")).as("no html part when none is given").isFalse();
    }

    @Test
    @DisplayName("A rejected request fails with a message that holds no key or recipient")
    void rejectedRequestFailsSafely() {
        responseStatus = 401;

        assertThatThrownBy(() -> sender(stubEndpoint()).send(RECIPIENT, "Subject", "Body", null))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessage("Azure email rejected the message (HTTP 401)")
                .message().doesNotContain(accessKey).doesNotContain(RECIPIENT);
    }

    @Test
    @DisplayName("An unreachable endpoint fails with a safe message")
    void unreachableEndpointFailsSafely() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }

        assertThatThrownBy(() -> sender("http://127.0.0.1:" + closedPort).send(RECIPIENT, "Subject", "Body", null))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessage("Azure email service unreachable");
    }

    /** Recomputes the ACS HMAC-SHA256 signature. Returns null when valid, otherwise the reason. */
    private String verifySignature(String method, String pathAndQuery, String host, String date,
                                   String contentHash, String authorization, byte[] body) {
        try {
            if (!AzureCommunicationEmailSender.SEND_PATH.equals(pathAndQuery)) {
                return "unexpected path " + pathAndQuery;
            }
            if (!"Sat, 14 Nov 2026 10:15:30 GMT".equals(date)) {
                return "unexpected x-ms-date " + date;
            }
            String expectedHash = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(body));
            if (!expectedHash.equals(contentHash)) {
                return "content hash mismatch";
            }
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(Base64.getDecoder().decode(accessKey), "HmacSHA256"));
            String toSign = method + "\n" + pathAndQuery + "\n" + date + ";" + host + ";" + contentHash;
            String expectedSignature = Base64.getEncoder().encodeToString(mac.doFinal(toSign.getBytes(StandardCharsets.UTF_8)));
            String expectedAuth = "HMAC-SHA256 SignedHeaders=x-ms-date;host;x-ms-content-sha256&Signature=" + expectedSignature;
            return expectedAuth.equals(authorization) ? null : "signature mismatch";
        } catch (Exception e) {
            return "verification error: " + e;
        }
    }
}
