package com.swiftdrop.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.swiftdrop.analytics.config.RabbitMQConfig;
import com.swiftdrop.analytics.messaging.TelemetryEventDecoder;
import com.swiftdrop.analytics.service.TelemetryProcessingService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
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
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/** Requires Docker: verify must fail rather than silently skip this evidence. */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TelemetryConsumptionIT {
    private static final String STATUS = "parcel.status.changed";
    private static final String ASSIGNED = "dispatch.courier.assigned";
    private static final String CREATED = "order.created";

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

    @BeforeAll
    void initialize() throws Exception {
        // Importing definitions suppresses RabbitMQ's default guest creation.
        // Use the in-container CLI; rabbitmqadmin's default guest is absent.
        assertThat(rabbit.execInContainer("rabbitmqctl", "add_user", "analytics-it", "analytics-it").getExitCode()).isZero();
        assertThat(rabbit.execInContainer("rabbitmqctl", "set_permissions", "-p", "/",
                "analytics-it", ".*", ".*", ".*").getExitCode()).isZero();
        startApplication();
    }

    private void startApplication() {
        context = new SpringApplicationBuilder(AnalyticsAiApplication.class).web(WebApplicationType.NONE).run(
                "--spring.datasource.url=" + postgres.getJdbcUrl(),
                "--spring.datasource.username=" + postgres.getUsername(),
                "--spring.datasource.password=" + postgres.getPassword(),
                "--spring.rabbitmq.host=" + rabbit.getHost(),
                "--spring.rabbitmq.port=" + rabbit.getAmqpPort(),
                "--spring.rabbitmq.username=analytics-it",
                "--spring.rabbitmq.password=analytics-it",
                "--spring.main.banner-mode=off",
                "--logging.level.root=WARN");
        jdbc = context.getBean(JdbcTemplate.class);
        mapper = context.getBean(ObjectMapper.class);
        template = context.getBean(RabbitTemplate.class);
        admin = new RabbitAdmin(template.getConnectionFactory());
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(admin.getQueueInfo(RabbitMQConfig.ANALYTICS_EVENTS_QUEUE).getConsumerCount()).isPositive());
    }

    @AfterAll
    void stopApplication() {
        if (context != null) context.close();
    }

    @BeforeEach
    void resetDedicatedTestStorage() {
        var listener = context.getBean(RabbitListenerEndpointRegistry.class).getListenerContainer("analyticsTelemetry");
        listener.stop(); // Wait for in-flight deliveries before resetting this test's storage.
        admin.purgeQueue(RabbitMQConfig.ANALYTICS_EVENTS_QUEUE, false);
        admin.purgeQueue(RabbitMQConfig.DLQ_ANALYTICS_EVENTS_QUEUE, false);
        jdbc.execute("DROP TRIGGER IF EXISTS fail_projection ON operational_sla_snapshots");
        jdbc.execute("DROP FUNCTION IF EXISTS test_projection_failure()");
        jdbc.execute("DROP SEQUENCE IF EXISTS test_projection_attempts");
        jdbc.execute("TRUNCATE processed_telemetry_events, operational_sla_snapshots, courier_performance_metrics RESTART IDENTITY");
        listener.start();
    }

    private ObjectNode base() {
        ObjectNode tree = mapper.createObjectNode();
        tree.put("eventId", UUID.randomUUID().toString());
        tree.put("orderId", UUID.randomUUID().toString());
        tree.put("correlationId", UUID.randomUUID().toString());
        tree.put("timestamp", Instant.now().toString());
        return tree;
    }

    private ObjectNode status() {
        ObjectNode tree = base();
        tree.put("trackingNumber", "SD-IT-001");
        tree.put("newPublicStatus", "DELIVERED");
        tree.put("district", "COLOMBO");
        return tree;
    }

    private Message received(String key, ObjectNode tree) {
        MessageProperties properties = new MessageProperties();
        properties.setReceivedRoutingKey(key);
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        return new Message(tree.toString().getBytes(StandardCharsets.UTF_8), properties);
    }

    private void publish(String key, ObjectNode tree) {
        template.send(RabbitMQConfig.EVENTS_EXCHANGE, key, received(key, tree));
    }

    private int markers() {
        return jdbc.queryForObject("SELECT count(*) FROM processed_telemetry_events", Integer.class);
    }

    private int parcelEffects() {
        return jdbc.queryForObject("SELECT coalesce(sum(total_parcels), 0) FROM operational_sla_snapshots", Integer.class);
    }

    private void awaitEffects(int expected) {
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(markers()).isEqualTo(expected);
            assertThat(parcelEffects()).isEqualTo(expected);
        });
    }

    private Message awaitDeadLetter() {
        Message[] result = new Message[1];
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            result[0] = template.receive(RabbitMQConfig.DLQ_ANALYTICS_EVENTS_QUEUE);
            assertThat(result[0]).isNotNull();
        });
        return result[0];
    }

    @Test
    void brokerDuplicateHasOneDatabaseEffectAndPreservesMetadata() {
        ObjectNode tree = status();
        // Must not cause Java class loading or pre-validation conversion.
        Message raw = received(STATUS, tree);
        raw.getMessageProperties().setHeader("__TypeId__", "untrusted.DoesNotExist");
        raw.getMessageProperties().setContentType("application/x-java-serialized-object");
        template.send(RabbitMQConfig.EVENTS_EXCHANGE, STATUS, raw);
        publish(STATUS, tree);
        awaitEffects(1);
        await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(parcelEffects()).isEqualTo(1));
        assertThat(jdbc.queryForObject("SELECT correlation_id::text FROM processed_telemetry_events", String.class))
                .isEqualTo(tree.get("correlationId").asText());
        assertThat(jdbc.queryForObject("SELECT event_id::text FROM processed_telemetry_events", String.class))
                .isEqualTo(tree.get("eventId").asText());
    }

    @Test
    void concurrentTransactionsForSameEventHaveOneEffect() throws Exception {
        ObjectNode tree = status();
        var event = context.getBean(TelemetryEventDecoder.class).decode(received(STATUS, tree)).orElseThrow();
        var service = context.getBean(TelemetryProcessingService.class);
        try (var executor = Executors.newFixedThreadPool(8)) {
            CountDownLatch ready = new CountDownLatch(8);
            CountDownLatch go = new CountDownLatch(1);
            var tasks = new ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 8; i++) {
                tasks.add(executor.submit(() -> {
                    ready.countDown();
                    if (!go.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start timed out");
                    service.process(event);
                    return null;
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            for (var task : tasks) task.get(20, TimeUnit.SECONDS);
        }
        assertThat(markers()).isEqualTo(1);
        assertThat(parcelEffects()).isEqualTo(1);
    }

    private void installProjectionFailure(int failAttempts) {
        // PostgreSQL sequences are intentionally nontransactional: count actual
        // attempts even though each failed inbox/projection transaction rolls back.
        jdbc.execute("CREATE SEQUENCE test_projection_attempts");
        jdbc.execute("""
                CREATE FUNCTION test_projection_failure() RETURNS trigger AS $$
                BEGIN
                    IF nextval('test_projection_attempts') <= %d THEN
                        RAISE EXCEPTION 'intentional test-only projection failure';
                    END IF;
                    RETURN NEW;
                END;
                $$ LANGUAGE plpgsql
                """.formatted(failAttempts));
        jdbc.execute("CREATE TRIGGER fail_projection AFTER INSERT ON operational_sla_snapshots "
                + "FOR EACH ROW EXECUTE FUNCTION test_projection_failure()");
    }

    @Test
    void rollbackRemovesMarkerAndProjectionThenSameEventCanRetry() {
        ObjectNode tree = status();
        var event = context.getBean(TelemetryEventDecoder.class).decode(received(STATUS, tree)).orElseThrow();
        var service = context.getBean(TelemetryProcessingService.class);
        installProjectionFailure(100);
        assertThatThrownBy(() -> service.process(event)).isInstanceOf(RuntimeException.class);
        assertThat(markers()).isZero();
        assertThat(parcelEffects()).isZero();
        jdbc.execute("DROP TRIGGER fail_projection ON operational_sla_snapshots");
        service.process(event);
        assertThat(markers()).isEqualTo(1);
        assertThat(parcelEffects()).isEqualTo(1);
    }

    @Test
    void transientDatabaseFailureSucceedsOnThirdBrokerAttempt() {
        installProjectionFailure(2);
        publish(STATUS, status());
        awaitEffects(1);
        assertThat(jdbc.queryForObject("SELECT last_value FROM test_projection_attempts", Long.class)).isEqualTo(3L);
        assertThat(template.receive(RabbitMQConfig.DLQ_ANALYTICS_EVENTS_QUEUE)).isNull();
    }

    @Test
    void permanentDatabaseFailureIsDeadLetteredAfterThreeAttemptsAndCanBeReplayed() {
        installProjectionFailure(100);
        ObjectNode tree = status();
        publish(STATUS, tree);
        Message dead = awaitDeadLetter();
        assertThat(jdbc.queryForObject("SELECT last_value FROM test_projection_attempts", Long.class)).isEqualTo(3L);
        assertThat(markers()).isZero();
        assertThat(parcelEffects()).isZero();
        assertThat(dead.getMessageProperties().getHeaders()).containsKey("x-death");
        jdbc.execute("DROP TRIGGER fail_projection ON operational_sla_snapshots");
        template.send(RabbitMQConfig.EVENTS_EXCHANGE, STATUS, dead);
        awaitEffects(1);
    }

    @Test
    void missingStableIdAndMalformedSupportedJsonGoToDlqWithoutEffects() {
        ObjectNode tree = status();
        tree.remove("eventId");
        publish(STATUS, tree);
        Message missingId = awaitDeadLetter();
        assertThat(new String(missingId.getBody(), StandardCharsets.UTF_8)).isEqualTo(tree.toString());
        template.send(RabbitMQConfig.EVENTS_EXCHANGE, STATUS,
                new Message("not-json".getBytes(StandardCharsets.UTF_8), new MessageProperties()));
        assertThat(new String(awaitDeadLetter().getBody(), StandardCharsets.UTF_8)).isEqualTo("not-json");
        assertThat(markers()).isZero();
        assertThat(parcelEffects()).isZero();
    }

    @Test
    void unsupportedDispatchEventIsAcknowledgedWithoutProjectionOrDlq() {
        publish("dispatch.failed", base());
        // A valid message behind it proves the queue has advanced past it.
        publish(STATUS, status());
        awaitEffects(1);
        assertThat(template.receive(RabbitMQConfig.DLQ_ANALYTICS_EVENTS_QUEUE)).isNull();
    }

    @Test
    void assignmentRedeliveryCountsOnce() {
        ObjectNode tree = base();
        tree.put("courierId", UUID.randomUUID().toString());
        tree.put("courierName", "Integration Test Courier");
        tree.put("assignedAt", Instant.now().toString());
        tree.put("estimatedPickupMinutes", 10);
        publish(ASSIGNED, tree);
        publish(ASSIGNED, tree);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(markers()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT sum(total_assigned) FROM courier_performance_metrics", Integer.class)).isEqualTo(1);
        });
    }

    @Test
    void orderCreatedIsBoundAndRetainedOnceForFutureProjectionWithoutUnknownFields() {
        ObjectNode tree = base();
        tree.put("trackingNumber", "SD-IT-CREATED");
        tree.put("pickupDistrict", "COLOMBO");
        tree.put("deliveryDistrict", "KANDY");
        tree.put("unexpectedPrivateField", "must not be retained");
        publish(CREATED, tree);
        publish(CREATED, tree);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(markers()).isEqualTo(1));
        assertThat(parcelEffects()).isZero();
        assertThat(jdbc.queryForObject("SELECT payload::text FROM processed_telemetry_events", String.class))
                .contains("SD-IT-CREATED").doesNotContain("unexpectedPrivateField", "must not be retained");
    }

    @Test
    void realApplicationRestartDoesNotForgetProcessedIds() {
        ObjectNode tree = status();
        publish(STATUS, tree);
        awaitEffects(1);
        context.close();
        startApplication();
        publish(STATUS, tree);
        // Barrier message behind the duplicate proves it was consumed after restart.
        ObjectNode second = status();
        publish(STATUS, second);
        awaitEffects(2);
    }

    @Test
    void upgradeFromV2PreservesExistingRowsAndAddsInbox() throws Exception {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas("upgrade_test").defaultSchema("upgrade_test").target("2").load().migrate();
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO upgrade_test.operational_sla_snapshots(district, total_parcels) VALUES ('KANDY', 7)");
            var upgrade = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .schemas("upgrade_test").defaultSchema("upgrade_test").load();
            assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
            upgrade.validate();
            try (var result = statement.executeQuery("SELECT total_parcels FROM upgrade_test.operational_sla_snapshots")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(7);
            }
            try (var result = statement.executeQuery("SELECT count(*) FROM upgrade_test.processed_telemetry_events")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isZero();
            }
        }
    }
}
