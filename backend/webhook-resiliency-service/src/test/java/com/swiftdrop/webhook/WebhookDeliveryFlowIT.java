package com.swiftdrop.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.events.delivery.ParcelStatusChangedEvent;
import com.swiftdrop.webhook.config.RabbitMQConfig;
import com.swiftdrop.webhook.delivery.WebhookHeaders;
import com.swiftdrop.webhook.delivery.WebhookSigner;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * The whole integration flow: an event on a real RabbitMQ is stored in a real PostgreSQL, and the worker sends it,
 * signed, to a real HTTP server standing in for the merchant's shop.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class WebhookDeliveryFlowIT {

    private static final Duration WAIT = Duration.ofSeconds(30);

    private record Received(Map<String, List<String>> headers, byte[] body) {
        String header(String name) {
            return headers.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(name))
                    .map(e -> e.getValue().getFirst()).findFirst().orElse(null);
        }
    }

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    private static final List<Received> received = new CopyOnWriteArrayList<>();
    private static volatile int shopAnswers = 200;
    private static final HttpServer shop = startShop();

    private static HttpServer startShop() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/hook", exchange -> {
                received.add(new Received(Map.copyOf(exchange.getRequestHeaders()), exchange.getRequestBody().readAllBytes()));
                exchange.sendResponseHeaders(shopAnswers, -1);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException unavailable) {
            throw new IllegalStateException(unavailable);
        }
    }

    @AfterAll
    static void stopShop() {
        shop.stop(0);
    }

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbit::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbit::getAdminPassword);
        // The stand-in shop is on this machine; production allows public HTTPS only.
        registry.add("webhook.security.dev-http-allowlist", () -> "localhost:" + shop.getAddress().getPort());
        registry.add("webhook.delivery.retry-delays", () -> "200ms,200ms");
        registry.add("webhook.delivery.jitter-ratio", () -> "0");
        registry.add("webhook.delivery.poll-interval", () -> "200ms");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private JdbcTemplate jdbc;

    private final UUID merchant = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE webhook_delivery_attempts, webhook_deliveries, order_merchant_map, inbound_events, "
                + "webhook_endpoints, dead_letter_webhook_records CASCADE");
        received.clear();
        shopAnswers = 200;
    }

    @Test
    @DisplayName("A status event reaches the shop once, signed with its secret, even when the broker delivers it twice")
    void eventReachesTheShopSigned() throws Exception {
        JsonNode created = register();
        String secret = created.get("signingSecret").asText();
        UUID order = order(merchant);

        ParcelStatusChangedEvent event = status(order);
        publish(event);
        publish(event);

        await().atMost(WAIT).untilAsserted(() -> assertThat(received).hasSize(1));
        Received request = received.getFirst();
        assertThat(request.header(WebhookHeaders.EVENT)).isEqualTo("parcel.status.changed");
        assertThat(request.header(WebhookHeaders.EVENT_ID)).isEqualTo(event.eventId().toString());
        assertThat(request.header(WebhookHeaders.ATTEMPT)).isEqualTo("1");
        long sentAt = Long.parseLong(request.header(WebhookHeaders.TIMESTAMP));
        assertThat(request.header(WebhookHeaders.SIGNATURE))
                .isEqualTo(new WebhookSigner().sign(secret, sentAt, request.body()));
        String body = new String(request.body());
        assertThat(body).contains("OUT_FOR_DELIVERY").contains("SD-7K2P-Q9XM");
        // The customer is nobody's business but ours.
        assertThat(body).doesNotContain(event.customerId().toString());

        await().atMost(WAIT).untilAsserted(() -> assertThat(deliveries("SUCCEEDED")).isEqualTo(1));
        assertThat(received).hasSize(1);
        String endpoint = created.at("/endpoint/endpointId").asText();
        assertThat(ok(get("/api/v1/webhooks/subscriptions/" + endpoint + "/attempts"), merchant)).singleElement()
                .satisfies(row -> assertThat(row.get("httpStatusCode").asInt()).isEqualTo(200));
    }

    @Test
    @DisplayName("A shop that keeps failing is retried, then listed as failed, and a replay delivers it")
    void failedDeliveryIsRetriedListedAndReplayed() throws Exception {
        register();
        UUID order = order(merchant);
        shopAnswers = 503;
        publish(status(order));

        await().atMost(WAIT).untilAsserted(() -> assertThat(deliveries("DEAD")).isEqualTo(1));
        assertThat(received).hasSize(3);
        assertThat(received).extracting(r -> r.header(WebhookHeaders.ATTEMPT)).containsExactly("1", "2", "3");
        assertThat(ok(get("/api/v1/webhooks/deliveries/failed"), stranger)).isEmpty();
        JsonNode failed = ok(get("/api/v1/webhooks/deliveries/failed"), merchant).get(0);
        assertThat(failed.get("lastHttpStatus").asInt()).isEqualTo(503);

        String path = "/api/v1/webhooks/deliveries/failed/" + failed.get("deliveryId").asText() + "/replay";
        status(post(path), stranger, 404);
        shopAnswers = 200;
        ok(post(path), merchant);
        await().atMost(WAIT).untilAsserted(() -> assertThat(deliveries("SUCCEEDED")).isEqualTo(1));
        assertThat(received).hasSize(4);
        assertThat(ok(post("/api/v1/webhooks/deliveries/failed/replay"), merchant)).isNotNull();
    }

    @Test
    @DisplayName("A refused request (400) is not retried; a redirect is never followed")
    void permanentFailuresAreNotRetried() throws Exception {
        register();
        UUID order = order(merchant);
        shopAnswers = 400;
        publish(status(order));
        await().atMost(WAIT).untilAsserted(() -> assertThat(deliveries("DEAD")).isEqualTo(1));
        shopAnswers = 302;
        publish(status(order));
        await().atMost(WAIT).untilAsserted(() -> assertThat(deliveries("DEAD")).isEqualTo(2));
        assertThat(received).hasSize(2);
    }

    @Test
    @DisplayName("An event of another shop's order, or of an order nobody's shop sent, is not sent")
    void otherOrdersAreNotSent() throws Exception {
        register();
        publish(status(order(stranger)));
        publish(status(order(null)));
        publish(status(order(merchant)));

        await().atMost(WAIT).untilAsserted(() -> assertThat(deliveries("SUCCEEDED")).isEqualTo(1));
        assertThat(received).hasSize(1);
    }

    @Test
    @DisplayName("A status event that overtakes its order waits for it")
    void overtakingEventWaits() throws Exception {
        register();
        UUID order = UUID.randomUUID();
        publish(status(order));
        await().atMost(WAIT).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM inbound_events WHERE processing_status = 'AWAITING_MERCHANT'", Integer.class)).isEqualTo(1));
        assertThat(received).isEmpty();

        orderCreated(order, merchant);
        await().atMost(WAIT).untilAsserted(() -> assertThat(received).hasSize(1));
    }

    @Test
    @DisplayName("A message that is not a valid event goes to the dead-letter queue")
    void invalidMessageIsDeadLettered() {
        rabbitTemplate.convertAndSend(RabbitMQConfig.EVENTS_EXCHANGE, "parcel.status.changed", Map.of("orderId", "not-a-uuid"));

        assertThat(rabbitTemplate.receive(RabbitMQConfig.DLQ_WEBHOOK_EVENTS_QUEUE, WAIT.toMillis())).isNotNull();
    }

    @Test
    @DisplayName("Endpoints: only the shop and an admin manage them; unsafe addresses are refused; the secret is shown once")
    void endpointManagement() throws Exception {
        JsonNode created = register();
        String path = "/api/v1/webhooks/subscriptions/" + created.at("/endpoint/endpointId").asText();

        assertThat(ok(get(path), merchant).toString()).doesNotContain("whsec_");
        status(get(path), stranger, 404);
        status(delete(path), stranger, 404);
        status(as(get(path), UUID.randomUUID(), "COURIER", null), 403);
        status(get(path), 401);
        status(registration("http://10.0.0.1/hook", "parcel.*"), 400);
        status(registration("https://127.0.0.1/hook", "parcel.*"), 400);
        status(registration(hook(), "no.such.event"), 400);
        status(registration("not a url", "parcel.*"), 400);

        assertThat(ok(post(path + "/test"), merchant).get("success").asBoolean()).isTrue();
        assertThat(received).hasSize(1);
        assertThat(ok(patch(path + "/status").param("active", "false"), merchant).get("active").asBoolean()).isFalse();
        // A switched-off endpoint gets nothing until it is on again.
        publish(status(order(merchant)));
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(4)).untilAsserted(() -> assertThat(received).hasSize(1));
        ok(patch(path + "/status").param("active", "true"), merchant);
        await().atMost(WAIT).untilAsserted(() -> assertThat(received).hasSize(2));
        assertThat(ok(get("/api/v1/webhooks/subscriptions"), merchant)).hasSize(1);
        // Its deliveries are history, so it is switched off rather than deleted; one without any can go.
        status(delete(path), merchant, 409);
        String unused = "/api/v1/webhooks/subscriptions/" + register().at("/endpoint/endpointId").asText();
        ok(delete(unused), merchant);
        assertThat(ok(get("/api/v1/webhooks/subscriptions"), merchant)).hasSize(1);
    }

    @Test
    @DisplayName("An old dead-letter record is replayed to its shop once it works again, by its owner only")
    void legacyDeadLetterReplay() throws Exception {
        UUID endpoint = UUID.fromString(register().at("/endpoint/endpointId").asText());
        UUID record = UUID.randomUUID();
        jdbc.update("INSERT INTO dead_letter_webhook_records(dlq_id, endpoint_id, merchant_id, event_type, payload, failure_reason) "
                + "VALUES (?, ?, ?, 'parcel.status.changed', '{\"status\":\"DELIVERED\"}', 'HTTP_SERVER_ERROR HTTP 503')",
                record, endpoint, merchant);
        String path = "/api/v1/webhooks/dlq/" + record;

        status(get(path), stranger, 404);
        status(as(post(path + "/replay"), UUID.randomUUID(), "DISPATCHER", null), 403);
        assertThat(ok(get(path), merchant).get("replayed").asBoolean()).isFalse();
        shopAnswers = 500;
        assertThat(ok(post(path + "/replay"), merchant).get("status").asText()).isEqualTo("FAILED");
        shopAnswers = 200;
        assertThat(ok(post(path + "/replay"), merchant).get("status").asText()).isEqualTo("SUCCESS");
        assertThat(ok(get("/api/v1/webhooks/dlq?unreplayedOnly=false"), merchant)).isNotEmpty();
    }

    // ---- steps

    private String hook() {
        return "http://localhost:" + shop.getAddress().getPort() + "/hook";
    }

    private JsonNode register() throws Exception {
        MvcResult result = mockMvc.perform(registration(hook(), "parcel.status.changed")).andReturn();
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(201);
        return json.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private MockHttpServletRequestBuilder registration(String url, String events) throws Exception {
        return as(post("/api/v1/webhooks/subscriptions"), UUID.randomUUID(), "MERCHANT", merchant)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("targetUrl", url, "subscribedEvents", events)));
    }

    /** An order known to the service, owned by {@code owner} (or by no shop). */
    private UUID order(UUID owner) {
        UUID order = UUID.randomUUID();
        orderCreated(order, owner);
        await().atMost(WAIT).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM order_merchant_map WHERE order_id = ?", Integer.class, order)).isEqualTo(1));
        return order;
    }

    private void orderCreated(UUID order, UUID owner) {
        var event = json.createObjectNode().put("eventId", UUID.randomUUID().toString())
                .put("correlationId", UUID.randomUUID().toString()).put("orderId", order.toString())
                .put("trackingNumber", "SD-7K2P-Q9XM").put("timestamp", Instant.now().toString());
        if (owner != null) {
            event.put("merchantId", owner.toString());
        }
        rabbitTemplate.convertAndSend(RabbitMQConfig.EVENTS_EXCHANGE, "order.created", event);
    }

    private static ParcelStatusChangedEvent status(UUID order) {
        return new ParcelStatusChangedEvent(UUID.randomUUID(), UUID.randomUUID(), "SD-7K2P-Q9XM", order, UUID.randomUUID(),
                PublicOrderStatus.IN_TRANSIT, PublicOrderStatus.OUT_FOR_DELIVERY, null,
                SriLankanDistrict.COLOMBO, SriLankanDistrict.KANDY, null, Instant.now());
    }

    private void publish(ParcelStatusChangedEvent event) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.EVENTS_EXCHANGE, "parcel.status.changed", event);
    }

    private int deliveries(String status) {
        return jdbc.queryForObject("SELECT count(*) FROM webhook_deliveries WHERE status = ?", Integer.class, status);
    }

    private JsonNode ok(MockHttpServletRequestBuilder request, UUID shopId) throws Exception {
        MvcResult result = mockMvc.perform(as(request, UUID.randomUUID(), "MERCHANT", shopId)).andReturn();
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isBetween(200, 299);
        return json.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private void status(MockHttpServletRequestBuilder request, UUID shopId, int expected) throws Exception {
        status(as(request, UUID.randomUUID(), "MERCHANT", shopId), expected);
    }

    private void status(MockHttpServletRequestBuilder request, int expected) throws Exception {
        MvcResult result = mockMvc.perform(request).andReturn();
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(expected);
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, UUID user, String role, UUID shopId) {
        request.header("X-User-Id", user.toString()).header("X-User-Role", role);
        if (shopId != null) {
            request.header("X-Merchant-Id", shopId.toString());
        }
        return request;
    }
}
