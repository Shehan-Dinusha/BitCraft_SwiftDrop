package com.swiftdrop.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rabbitmq.client.GetResponse;
import com.swiftdrop.webhook.config.RabbitMQConfig;
import com.swiftdrop.webhook.domain.entity.WebhookEndpoint;
import com.swiftdrop.webhook.domain.repository.WebhookEndpointRepository;
import com.swiftdrop.webhook.messaging.consumer.InboundEventConsumer;
import com.swiftdrop.webhook.messaging.decoder.DecodedInboundEvent;
import com.swiftdrop.webhook.messaging.decoder.InboundEventDecoder;
import com.swiftdrop.webhook.service.ingestion.InboundEventIngestionService;
import com.swiftdrop.webhook.service.ingestion.IngestionResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.amqp.rabbit.support.DefaultMessagePropertiesConverter;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Phase 2: ingestion through a real RabbitMQ broker (loaded from the repository's {@code definitions.json}, so
 * Java/broker declaration parity is exercised) into real PostgreSQL. Requires Docker.
 *
 * <p>The listener runs with one consumer so that a "barrier" event published last proves every earlier message
 * on the queue was consumed.</p>
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WebhookIngestionIT {

    private static final String STATUS = "parcel.status.changed";
    private static final String CREATED = "order.created";
    private static final Duration WAIT = Duration.ofSeconds(20);

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management-alpine")
            .withCopyFileToContainer(MountableFile.forHostPath(
                    Path.of("../../infra/local/rabbitmq/definitions.json").toAbsolutePath()),
                    "/etc/rabbitmq/definitions.json")
            .withCopyToContainer(Transferable.of("""
                    loopback_users.guest = false
                    management.load_definitions = /etc/rabbitmq/definitions.json
                    """), "/etc/rabbitmq/rabbitmq.conf");

    private ConfigurableApplicationContext context;
    private JdbcTemplate jdbc;
    private ObjectMapper mapper;
    private RabbitTemplate template;
    private RabbitAdmin admin;
    private WebhookEndpointRepository endpoints;

    @BeforeAll
    void start() throws Exception {
        // Importing definitions suppresses the default guest user.
        assertThat(rabbit.execInContainer("rabbitmqctl", "add_user", "webhook-it", "webhook-it").getExitCode()).isZero();
        assertThat(rabbit.execInContainer("rabbitmqctl", "set_permissions", "-p", "/",
                "webhook-it", ".*", ".*", ".*").getExitCode()).isZero();
        context = new SpringApplicationBuilder(WebhookResiliencyApplication.class).web(WebApplicationType.NONE).run(
                "--spring.datasource.url=" + postgres.getJdbcUrl(),
                "--spring.datasource.username=" + postgres.getUsername(),
                "--spring.datasource.password=" + postgres.getPassword(),
                "--spring.rabbitmq.host=" + rabbit.getHost(),
                "--spring.rabbitmq.port=" + rabbit.getAmqpPort(),
                "--spring.rabbitmq.username=webhook-it",
                "--spring.rabbitmq.password=webhook-it",
                "--spring.rabbitmq.listener.simple.concurrency=1",
                "--spring.rabbitmq.listener.simple.max-concurrency=1",
                "--webhook.ingestion.retry.initial-interval=200ms",
                "--webhook.ingestion.retry.max-interval=1s",
                "--spring.main.banner-mode=off",
                "--logging.level.root=WARN");
        jdbc = context.getBean(JdbcTemplate.class);
        mapper = context.getBean(ObjectMapper.class);
        template = context.getBean(RabbitTemplate.class);
        admin = new RabbitAdmin(template.getConnectionFactory());
        endpoints = context.getBean(WebhookEndpointRepository.class);
        await().atMost(WAIT).untilAsserted(() ->
                assertThat(admin.getQueueInfo(RabbitMQConfig.WEBHOOK_EVENTS_QUEUE).getConsumerCount()).isPositive());
    }

    @AfterAll
    void stop() {
        if (context != null) {
            context.close();
        }
    }

    @BeforeEach
    void reset() {
        var listener = listener();
        listener.stop();
        admin.purgeQueue(RabbitMQConfig.WEBHOOK_EVENTS_QUEUE, false);
        admin.purgeQueue(RabbitMQConfig.DLQ_WEBHOOK_EVENTS_QUEUE, false);
        jdbc.execute("DROP TRIGGER IF EXISTS fail_delivery_insert ON webhook_deliveries");
        jdbc.execute("DROP FUNCTION IF EXISTS test_delivery_failure()");
        jdbc.execute("DROP SEQUENCE IF EXISTS test_delivery_attempts");
        jdbc.execute("TRUNCATE webhook_delivery_attempts, webhook_deliveries, order_merchant_map, inbound_events, "
                + "webhook_endpoints RESTART IDENTITY");
        listener.start();
    }

    // --- Topology ------------------------------------------------------------------------------------------------

    @Test
    void javaDeclarationsMatchBrokerDefinitionsAndBindTheIntendedEvents() throws Exception {
        String queues = rabbit.execInContainer("rabbitmqctl", "list_queues", "-q", "name", "type", "arguments").getStdout();
        assertThat(queues.lines().filter(l -> l.startsWith(RabbitMQConfig.WEBHOOK_EVENTS_QUEUE + "\t")).findFirst().orElseThrow())
                .contains("quorum").contains("x-message-ttl").contains("86400000").contains("dlq.webhook.events");
        assertThat(queues.lines().filter(l -> l.startsWith(RabbitMQConfig.DLQ_WEBHOOK_EVENTS_QUEUE + "\t")).findFirst().orElseThrow())
                .contains("quorum").contains("604800000");

        String bindings = rabbit.execInContainer("rabbitmqctl", "list_bindings", "-q",
                "source_name", "destination_name", "routing_key").getStdout();
        assertThat(bindings.lines().filter(l -> l.contains("\t" + RabbitMQConfig.WEBHOOK_EVENTS_QUEUE + "\t")))
                .extracting(l -> l.substring(l.lastIndexOf('\t') + 1))
                .contains("parcel.status.changed.#", "order.created");

        // Real producers publish these exact keys; both must arrive through the exchange.
        UUID merchant = UUID.randomUUID();
        endpoint(merchant, "*", true);
        UUID orderId = UUID.randomUUID();
        ObjectNode created = orderCreated(orderId, merchant);
        ObjectNode status = status(orderId, "PICKUP_ASSIGNED");
        publish(CREATED, created);
        publish(STATUS, status);
        awaitInbound(status);
        assertThat(inboundStatus(created)).isEqualTo("RESOLVED");
        assertThat(inboundStatus(status)).isEqualTo("RESOLVED");
    }

    // --- A. Basic ingestion -------------------------------------------------------------------------------------

    @Test
    void validEventsCreateInboxRowsAndPendingDeliveries() {
        UUID merchant = UUID.randomUUID();
        WebhookEndpoint endpoint = endpoint(merchant, "parcel.status.changed,order.created", true);
        UUID orderId = UUID.randomUUID();
        ObjectNode created = orderCreated(orderId, merchant);
        ObjectNode status = status(orderId, "IN_TRANSIT");

        publish(CREATED, created);
        publish(STATUS, status);
        awaitInbound(status);

        Map<String, Object> row = jdbc.queryForMap("""
                SELECT event_type, schema_version, correlation_id::text AS correlation_id, order_id::text AS order_id,
                       processing_status, merchant_id::text AS merchant_id, payload::text AS payload
                FROM inbound_events WHERE event_id = ?::uuid
                """, status.get("eventId").asText());
        assertThat(row).containsEntry("event_type", STATUS).containsEntry("schema_version", "1")
                .containsEntry("correlation_id", status.get("correlationId").asText())
                .containsEntry("order_id", orderId.toString())
                .containsEntry("processing_status", "RESOLVED")
                .containsEntry("merchant_id", merchant.toString());
        assertThat((String) row.get("payload")).contains("IN_TRANSIT").doesNotContain("customerId")
                .doesNotContain(status.get("customerId").asText());

        assertThat(deliveryEndpoints(status)).containsExactly(endpoint.getEndpointId());
        assertThat(deliveryEndpoints(created)).containsExactly(endpoint.getEndpointId());
        assertThat(jdbc.queryForMap("""
                SELECT status, attempt_count, next_attempt_at IS NOT NULL AS scheduled, lease_owner
                FROM webhook_deliveries d JOIN inbound_events e ON e.event_id = d.inbound_event_id
                WHERE e.event_id = ?::uuid
                """, status.get("eventId").asText()))
                .containsEntry("status", "PENDING").containsEntry("attempt_count", 0)
                .containsEntry("scheduled", true).containsEntry("lease_owner", null);
        assertThat(jdbc.queryForObject("SELECT merchant_id::text FROM order_merchant_map WHERE order_id = ?",
                String.class, orderId)).isEqualTo(merchant.toString());
    }

    // --- B. Duplicate broker delivery ---------------------------------------------------------------------------

    @Test
    void sameEventDeliveredTwiceCreatesOneInboxRowAndOneSetOfDeliveries() {
        UUID merchant = UUID.randomUUID();
        endpoint(merchant, STATUS, true);
        endpoint(merchant, "parcel.*", true);
        UUID orderId = UUID.randomUUID();
        publish(CREATED, orderCreated(orderId, merchant));
        ObjectNode status = status(orderId, "DELIVERED");
        publish(STATUS, status);
        publish(STATUS, status);
        ObjectNode barrier = barrier();

        awaitInbound(barrier);
        assertThat(count("SELECT count(*) FROM inbound_events WHERE event_id = ?::uuid", status.get("eventId").asText())).isEqualTo(1);
        assertThat(deliveryEndpoints(status)).hasSize(2);
        assertThat(dlqDepth()).isZero();
    }

    // --- C. Concurrent duplicate ingestion ----------------------------------------------------------------------

    @Test
    void concurrentIngestionOfTheSameEventCreatesDeliveriesOnce() throws Exception {
        UUID merchant = UUID.randomUUID();
        endpoint(merchant, STATUS, true);
        endpoint(merchant, "*", true);
        UUID orderId = UUID.randomUUID();
        ingest(CREATED, orderCreated(orderId, merchant));
        ObjectNode status = status(orderId, "OUT_FOR_DELIVERY");
        DecodedInboundEvent event = decode(STATUS, status);

        List<IngestionResult> results = runConcurrently(8, () -> service().ingest(event));

        assertThat(results).filteredOn(r -> r.outcome() != IngestionResult.Outcome.DUPLICATE).hasSize(1);
        assertThat(results.stream().mapToInt(IngestionResult::deliveriesCreated).sum()).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM inbound_events WHERE event_id = ?::uuid", status.get("eventId").asText())).isEqualTo(1);
        assertThat(deliveryEndpoints(status)).hasSize(2);
    }

    @Test
    void statusEventRacingItsOrderCreatedIsNeverLeftParked() throws Exception {
        UUID merchant = UUID.randomUUID();
        WebhookEndpoint endpoint = endpoint(merchant, STATUS, true);
        for (int round = 0; round < 25; round++) {
            UUID orderId = UUID.randomUUID();
            DecodedInboundEvent created = decode(CREATED, orderCreated(orderId, merchant));
            ObjectNode statusTree = status(orderId, "PICKUP_ASSIGNED");
            DecodedInboundEvent status = decode(STATUS, statusTree);
            List<DecodedInboundEvent> pair = List.of(created, status);
            var index = new java.util.concurrent.atomic.AtomicInteger();

            runConcurrently(2, () -> service().ingest(pair.get(index.getAndIncrement())));

            assertThat(inboundStatus(statusTree)).as("round %d", round).isEqualTo("RESOLVED");
            assertThat(deliveryEndpoints(statusTree)).as("round %d", round).containsExactly(endpoint.getEndpointId());
        }
    }

    // --- D. One merchant, many customers ------------------------------------------------------------------------

    @Test
    void eachParcelOfABulkSubmissionResolvesIndependentlyToItsMerchant() {
        UUID merchantA = UUID.randomUUID();
        WebhookEndpoint endpointA = endpoint(merchantA, STATUS, true);
        UUID a001 = UUID.randomUUID();
        UUID a002 = UUID.randomUUID();
        UUID a003 = UUID.randomUUID();
        // One bulk submission: three orders, three different customers, one merchant.
        for (UUID order : List.of(a001, a002, a003)) {
            ObjectNode created = orderCreated(order, merchantA);
            created.put("customerId", UUID.randomUUID().toString());
            publish(CREATED, created);
        }
        ObjectNode pickedUp = status(a001, "PICKED_UP");
        ObjectNode inTransit = status(a002, "IN_TRANSIT");
        ObjectNode delivered = status(a003, "DELIVERED");
        publish(STATUS, pickedUp);
        publish(STATUS, inTransit);
        publish(STATUS, delivered);
        awaitInbound(delivered);

        for (ObjectNode event : List.of(pickedUp, inTransit, delivered)) {
            assertThat(inboundMerchant(event)).isEqualTo(merchantA.toString());
            assertThat(deliveryEndpoints(event)).containsExactly(endpointA.getEndpointId());
        }
        // Three independent jobs, each tied to its own order's event.
        assertThat(jdbc.queryForList("""
                SELECT e.order_id::text FROM webhook_deliveries d JOIN inbound_events e ON e.event_id = d.inbound_event_id
                WHERE e.event_type = 'parcel.status.changed'
                """, String.class)).containsExactlyInAnyOrder(a001.toString(), a002.toString(), a003.toString());
    }

    // --- E. Cross-merchant isolation ----------------------------------------------------------------------------

    @Test
    void oneMerchantsEventsNeverReachAnotherMerchantsEndpoints() {
        UUID merchantA = UUID.randomUUID();
        UUID merchantB = UUID.randomUUID();
        WebhookEndpoint endpointA = endpoint(merchantA, "*", true);
        endpoint(merchantB, "*", true);
        UUID orderA = UUID.randomUUID();
        UUID orderB = UUID.randomUUID();
        publish(CREATED, orderCreated(orderA, merchantA));
        publish(CREATED, orderCreated(orderB, merchantB));
        ObjectNode statusA = status(orderA, "DELIVERED");
        // A status event for an order nobody has announced must not be broadcast to anyone.
        ObjectNode unknownOrder = status(UUID.randomUUID(), "DELIVERED");
        publish(STATUS, statusA);
        publish(STATUS, unknownOrder);
        awaitInbound(unknownOrder);

        assertThat(deliveryEndpoints(statusA)).containsExactly(endpointA.getEndpointId());
        assertThat(deliveryEndpoints(unknownOrder)).isEmpty();
        assertThat(inboundStatus(unknownOrder)).isEqualTo("AWAITING_MERCHANT");
        assertThat(count("""
                SELECT count(*) FROM webhook_deliveries d JOIN inbound_events e ON e.event_id = d.inbound_event_id
                WHERE e.order_id = ? AND d.merchant_id <> ?
                """, orderA, merchantA)).isZero();
    }

    // --- F. Multiple endpoints of one merchant ------------------------------------------------------------------

    @Test
    void everyMatchingActiveEndpointGetsItsOwnDelivery() {
        UUID merchant = UUID.randomUUID();
        WebhookEndpoint exact = endpoint(merchant, STATUS, true);
        WebhookEndpoint wildcard = endpoint(merchant, "parcel.*", true);
        endpoint(merchant, CREATED, true);                 // different event type
        endpoint(merchant, STATUS, false);                 // inactive
        endpoint(merchant, ",parcel, parcel.#", true);     // legacy row with invalid tokens: matches nothing
        UUID orderId = UUID.randomUUID();
        publish(CREATED, orderCreated(orderId, merchant));
        ObjectNode status = status(orderId, "IN_TRANSIT");
        publish(STATUS, status);
        awaitInbound(status);

        assertThat(deliveryEndpoints(status)).containsExactlyInAnyOrder(exact.getEndpointId(), wildcard.getEndpointId());
        assertThat(count("SELECT count(DISTINCT delivery_id) FROM webhook_deliveries d JOIN inbound_events e "
                + "ON e.event_id = d.inbound_event_id WHERE e.event_id = ?::uuid", status.get("eventId").asText())).isEqualTo(2);
    }

    // --- Merchant resolution edge cases ---------------------------------------------------------------------------

    @Test
    void statusEventArrivingBeforeItsOrderIsParkedThenResolvedByOrderCreated() {
        UUID merchant = UUID.randomUUID();
        WebhookEndpoint endpoint = endpoint(merchant, STATUS, true);
        UUID orderId = UUID.randomUUID();
        ObjectNode early = status(orderId, "PICKUP_ASSIGNED");
        publish(STATUS, early);
        awaitInbound(early);
        assertThat(inboundStatus(early)).isEqualTo("AWAITING_MERCHANT");
        assertThat(deliveryEndpoints(early)).isEmpty();

        ObjectNode created = orderCreated(orderId, merchant);
        publish(CREATED, created);
        awaitInbound(created);
        assertThat(inboundStatus(early)).isEqualTo("RESOLVED");
        assertThat(deliveryEndpoints(early)).containsExactly(endpoint.getEndpointId());
        assertThat(dlqDepth()).isZero();
    }

    @Test
    void customerOrderEventsAreResolvedAsHavingNoMerchant() {
        UUID merchant = UUID.randomUUID();
        endpoint(merchant, "*", true);
        UUID orderId = UUID.randomUUID();
        ObjectNode early = status(orderId, "CREATED");
        publish(STATUS, early);
        ObjectNode created = orderCreated(orderId, null);
        publish(CREATED, created);
        ObjectNode later = status(orderId, "DELIVERED");
        publish(STATUS, later);
        awaitInbound(later);

        assertThat(inboundStatus(early)).isEqualTo("NO_MERCHANT");
        assertThat(inboundStatus(created)).isEqualTo("NO_MERCHANT");
        assertThat(inboundStatus(later)).isEqualTo("NO_MERCHANT");
        assertThat(count("SELECT count(*) FROM webhook_deliveries")).isZero();
    }

    @Test
    void conflictingOwnershipClaimIsDeadLetteredAndTheFirstOwnerKept() {
        UUID merchantA = UUID.randomUUID();
        UUID merchantB = UUID.randomUUID();
        endpoint(merchantB, "*", true);
        UUID orderId = UUID.randomUUID();
        publish(CREATED, orderCreated(orderId, merchantA));
        ObjectNode conflicting = orderCreated(orderId, merchantB);
        publish(CREATED, conflicting);

        Message dead = awaitDeadLetter();
        assertThat(new String(dead.getBody(), StandardCharsets.UTF_8)).isEqualTo(conflicting.toString());
        assertThat(jdbc.queryForObject("SELECT merchant_id::text FROM order_merchant_map WHERE order_id = ?",
                String.class, orderId)).isEqualTo(merchantA.toString());
        assertThat(count("SELECT count(*) FROM inbound_events WHERE event_id = ?::uuid", conflicting.get("eventId").asText())).isZero();
        assertThat(count("SELECT count(*) FROM webhook_deliveries")).isZero();
    }

    // --- H. Database failure ------------------------------------------------------------------------------------

    @Test
    void transientDatabaseFailureIsRetriedAndPersistedOnce() {
        UUID merchant = UUID.randomUUID();
        endpoint(merchant, STATUS, true);
        UUID orderId = UUID.randomUUID();
        ingest(CREATED, orderCreated(orderId, merchant));
        installDeliveryInsertFailure(2);

        ObjectNode status = status(orderId, "IN_TRANSIT");
        publish(STATUS, status);
        awaitInbound(status);

        assertThat(jdbc.queryForObject("SELECT last_value FROM test_delivery_attempts", Long.class)).isEqualTo(3L);
        assertThat(deliveryEndpoints(status)).hasSize(1);
        assertThat(dlqDepth()).isZero();
    }

    @Test
    void persistentDatabaseFailureIsNeverAcknowledgedAsPersistedAndCanBeReplayed() {
        UUID merchant = UUID.randomUUID();
        endpoint(merchant, STATUS, true);
        UUID orderId = UUID.randomUUID();
        ingest(CREATED, orderCreated(orderId, merchant));
        installDeliveryInsertFailure(1_000);

        ObjectNode status = status(orderId, "IN_TRANSIT");
        publish(STATUS, status);
        Message dead = awaitDeadLetter();

        // Every attempt rolled back: no inbox row claims the event was processed.
        assertThat(jdbc.queryForObject("SELECT last_value FROM test_delivery_attempts", Long.class)).isEqualTo(3L);
        assertThat(count("SELECT count(*) FROM inbound_events WHERE event_id = ?::uuid", status.get("eventId").asText())).isZero();
        assertThat(xDeathCount(dead)).isEqualTo(1L);
        assertThat(queueDepth()).isZero();

        // After recovery an operator republishes the dead letter with its original routing key.
        jdbc.execute("DROP TRIGGER fail_delivery_insert ON webhook_deliveries");
        template.send(RabbitMQConfig.EVENTS_EXCHANGE, originalRoutingKey(dead), dead);
        awaitInbound(status);
        assertThat(deliveryEndpoints(status)).hasSize(1);
    }

    // --- I. Commit succeeded, acknowledgement lost --------------------------------------------------------------

    @Test
    void redeliveryAfterCommittedButUnacknowledgedMessageCreatesNoDuplicates() throws Exception {
        UUID merchant = UUID.randomUUID();
        endpoint(merchant, STATUS, true);
        endpoint(merchant, "*", true);
        UUID orderId = UUID.randomUUID();
        ingest(CREATED, orderCreated(orderId, merchant));
        ObjectNode status = status(orderId, "DELIVERED");

        listener().stop();
        publish(STATUS, status);

        // Take the message with manual acknowledgement on a raw connection, run the real consumer (its transaction
        // commits), then drop the connection without acknowledging: exactly a crash between commit and ack.
        var factory = new com.rabbitmq.client.ConnectionFactory();
        factory.setHost(rabbit.getHost());
        factory.setPort(rabbit.getAmqpPort());
        factory.setUsername("webhook-it");
        factory.setPassword("webhook-it");
        try (var connection = factory.newConnection()) {
            var channel = connection.createChannel();
            GetResponse[] holder = new GetResponse[1];
            await().atMost(WAIT).until(() -> (holder[0] = channel.basicGet(RabbitMQConfig.WEBHOOK_EVENTS_QUEUE, false)) != null);
            GetResponse response = holder[0];
            MessageProperties properties = new DefaultMessagePropertiesConverter()
                    .toMessageProperties(response.getProps(), response.getEnvelope(), "UTF-8");
            context.getBean(InboundEventConsumer.class).onMessage(new Message(response.getBody(), properties));
            assertThat(deliveryEndpoints(status)).hasSize(2);
            // No basicAck: closing the connection returns the message to the queue.
        }

        listener().start();
        ObjectNode barrier = barrier();
        awaitInbound(barrier);

        assertThat(count("SELECT count(*) FROM inbound_events WHERE event_id = ?::uuid", status.get("eventId").asText())).isEqualTo(1);
        assertThat(deliveryEndpoints(status)).hasSize(2);
        assertThat(queueDepth()).isZero();
        assertThat(dlqDepth()).isZero();
    }

    // --- J. Malformed and unsupported messages ------------------------------------------------------------------

    @Test
    void malformedMessageIsDeadLetteredOnceAndDoesNotBlockTheQueue() {
        template.send(RabbitMQConfig.EVENTS_EXCHANGE, STATUS,
                new Message("not-json".getBytes(StandardCharsets.UTF_8), new MessageProperties()));
        ObjectNode barrier = barrier();
        awaitInbound(barrier);

        Message dead = awaitDeadLetter();
        assertThat(new String(dead.getBody(), StandardCharsets.UTF_8)).isEqualTo("not-json");
        assertThat(xDeathCount(dead)).isEqualTo(1L);
        assertThat(originalRoutingKey(dead)).isEqualTo(STATUS);
        // Not requeued: nothing left on either queue afterwards.
        await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(queueDepth()).isZero();
            assertThat(dlqDepth()).isZero();
        });
    }

    @Test
    void invalidAndUnsupportedEventsAreDeadLetteredWithoutSideEffects() {
        ObjectNode missingId = status(UUID.randomUUID(), "DELIVERED");
        missingId.remove("eventId");
        publish(STATUS, missingId);

        Message futureVersion = message(status(UUID.randomUUID(), "DELIVERED"));
        futureVersion.getMessageProperties().setHeader(InboundEventDecoder.SCHEMA_VERSION_HEADER, "2");
        template.send(RabbitMQConfig.EVENTS_EXCHANGE, STATUS, futureVersion);

        // Bound for broker parity, never supported: must not be silently dropped.
        publish("webhook.dispatch.requested", status(UUID.randomUUID(), "DELIVERED"));

        ObjectNode barrier = barrier();
        awaitInbound(barrier);
        List<String> deadKeys = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            deadKeys.add(originalRoutingKey(awaitDeadLetter()));
        }
        assertThat(deadKeys).containsExactlyInAnyOrder(STATUS, STATUS, "webhook.dispatch.requested");
        assertThat(count("SELECT count(*) FROM inbound_events")).isEqualTo(1); // the barrier only
    }

    // --- helpers -------------------------------------------------------------------------------------------------

    private org.springframework.amqp.rabbit.listener.MessageListenerContainer listener() {
        return context.getBean(RabbitListenerEndpointRegistry.class).getListenerContainer(InboundEventConsumer.LISTENER_ID);
    }

    private InboundEventIngestionService service() {
        return context.getBean(InboundEventIngestionService.class);
    }

    private WebhookEndpoint endpoint(UUID merchant, String subscriptions, boolean active) {
        // Saved directly so tests can also create legacy rows that the API would reject.
        return endpoints.save(WebhookEndpoint.builder()
                .merchantId(merchant)
                .targetUrl("https://merchant.example.com/hooks/" + UUID.randomUUID())
                .secretKey("whsec_test")
                .subscribedEvents(subscriptions)
                .active(active)
                .build());
    }

    private ObjectNode base(UUID orderId) {
        ObjectNode tree = mapper.createObjectNode();
        tree.put("eventId", UUID.randomUUID().toString());
        tree.put("correlationId", UUID.randomUUID().toString());
        tree.put("orderId", orderId.toString());
        tree.put("customerId", UUID.randomUUID().toString());
        tree.put("timestamp", Instant.now().toString());
        return tree;
    }

    private ObjectNode orderCreated(UUID orderId, UUID merchantId) {
        ObjectNode tree = base(orderId);
        tree.put("trackingNumber", "SD-COL-20261110-" + (1000 + (int) (Math.random() * 9000)));
        if (merchantId != null) {
            tree.put("merchantId", merchantId.toString());
        }
        tree.put("pickupDistrict", "COLOMBO");
        tree.put("deliveryDistrict", "KANDY");
        return tree;
    }

    private ObjectNode status(UUID orderId, String newStatus) {
        ObjectNode tree = base(orderId);
        tree.put("trackingNumber", "SD-" + orderId.toString().substring(0, 8).toUpperCase());
        tree.put("newPublicStatus", newStatus);
        tree.put("district", "COLOMBO");
        return tree;
    }

    /** A valid event for an order no merchant owns; once it is in the inbox, all earlier messages were consumed. */
    private ObjectNode barrier() {
        ObjectNode tree = status(UUID.randomUUID(), "CREATED");
        publish(STATUS, tree);
        return tree;
    }

    private Message message(ObjectNode tree) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        return new Message(tree.toString().getBytes(StandardCharsets.UTF_8), properties);
    }

    private void publish(String routingKey, ObjectNode tree) {
        template.send(RabbitMQConfig.EVENTS_EXCHANGE, routingKey, message(tree));
    }

    private DecodedInboundEvent decode(String routingKey, ObjectNode tree) {
        Message message = message(tree);
        message.getMessageProperties().setReceivedRoutingKey(routingKey);
        return context.getBean(InboundEventDecoder.class).decode(message);
    }

    private IngestionResult ingest(String routingKey, ObjectNode tree) {
        return service().ingest(decode(routingKey, tree));
    }

    private <T> List<T> runConcurrently(int threads, java.util.concurrent.Callable<T> task) throws Exception {
        try (var executor = Executors.newFixedThreadPool(threads)) {
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!go.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("start timed out");
                    }
                    return task.call();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(20, TimeUnit.SECONDS));
            }
            return results;
        }
    }

    private void installDeliveryInsertFailure(int failAttempts) {
        // Sequences are not transactional, so they count attempts even though each failed transaction rolls back.
        jdbc.execute("CREATE SEQUENCE test_delivery_attempts");
        jdbc.execute("""
                CREATE FUNCTION test_delivery_failure() RETURNS trigger AS $$
                BEGIN
                    IF nextval('test_delivery_attempts') <= %d THEN
                        RAISE EXCEPTION 'intentional test-only database failure';
                    END IF;
                    RETURN NEW;
                END;
                $$ LANGUAGE plpgsql
                """.formatted(failAttempts));
        jdbc.execute("CREATE TRIGGER fail_delivery_insert BEFORE INSERT ON webhook_deliveries "
                + "FOR EACH ROW EXECUTE FUNCTION test_delivery_failure()");
    }

    private void awaitInbound(ObjectNode event) {
        await().atMost(WAIT).untilAsserted(() -> assertThat(count(
                "SELECT count(*) FROM inbound_events WHERE event_id = ?::uuid", event.get("eventId").asText())).isEqualTo(1));
    }

    private String inboundStatus(ObjectNode event) {
        return jdbc.queryForObject("SELECT processing_status FROM inbound_events WHERE event_id = ?::uuid",
                String.class, event.get("eventId").asText());
    }

    private String inboundMerchant(ObjectNode event) {
        return jdbc.queryForObject("SELECT merchant_id::text FROM inbound_events WHERE event_id = ?::uuid",
                String.class, event.get("eventId").asText());
    }

    private List<UUID> deliveryEndpoints(ObjectNode event) {
        return jdbc.queryForList("SELECT endpoint_id FROM webhook_deliveries WHERE inbound_event_id = ?::uuid",
                UUID.class, event.get("eventId").asText());
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private Message awaitDeadLetter() {
        Message[] result = new Message[1];
        await().atMost(WAIT).untilAsserted(() -> {
            result[0] = template.receive(RabbitMQConfig.DLQ_WEBHOOK_EVENTS_QUEUE);
            assertThat(result[0]).isNotNull();
        });
        return result[0];
    }

    private int queueDepth() {
        return admin.getQueueInfo(RabbitMQConfig.WEBHOOK_EVENTS_QUEUE).getMessageCount();
    }

    private int dlqDepth() {
        return admin.getQueueInfo(RabbitMQConfig.DLQ_WEBHOOK_EVENTS_QUEUE).getMessageCount();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstDeath(Message dead) {
        Object header = dead.getMessageProperties().getHeaders().get("x-death");
        assertThat(header).isInstanceOf(List.class);
        return ((List<Map<String, Object>>) header).getFirst();
    }

    private static long xDeathCount(Message dead) {
        return ((Number) firstDeath(dead).get("count")).longValue();
    }

    @SuppressWarnings("unchecked")
    private static String originalRoutingKey(Message dead) {
        return ((List<Object>) firstDeath(dead).get("routing-keys")).getFirst().toString();
    }
}
