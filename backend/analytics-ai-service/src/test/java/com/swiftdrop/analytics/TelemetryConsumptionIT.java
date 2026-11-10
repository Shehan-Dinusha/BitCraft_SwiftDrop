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
        context = new SpringApplicationBuilder(AnalyticsAiApplication.class).web(WebApplicationType.SERVLET).run(
                "--server.port=0",
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
        jdbc.execute("DROP TRIGGER IF EXISTS fail_projection ON daily_delivery_stats");
        jdbc.execute("DROP FUNCTION IF EXISTS test_projection_failure()");
        jdbc.execute("DROP SEQUENCE IF EXISTS test_projection_attempts");
        jdbc.execute("TRUNCATE processed_telemetry_events, operational_sla_snapshots, courier_performance_metrics, analytics_order_projection, daily_delivery_stats, courier_daily_stats, analytics_couriers, analytics_order_couriers RESTART IDENTITY");
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
        return jdbc.queryForObject("SELECT coalesce(sum(delivered), 0) FROM daily_delivery_stats", Integer.class);
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
        jdbc.execute("CREATE TRIGGER fail_projection AFTER INSERT ON daily_delivery_stats "
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
        jdbc.execute("DROP TRIGGER fail_projection ON daily_delivery_stats");
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
        jdbc.execute("DROP TRIGGER fail_projection ON daily_delivery_stats");
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
            assertThat(jdbc.queryForObject("SELECT count(*) FROM analytics_order_couriers", Integer.class)).isEqualTo(1);
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
            assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(2);
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

    private static final java.time.LocalDate DAY = java.time.LocalDate.of(2026, 11, 8);
    private static final String CREATED_AT = "2026-11-08T06:00:00Z";

    private ObjectNode created(UUID order, String at) {
        ObjectNode event = base();
        event.put("orderId", order.toString()).put("timestamp", at);
        event.put("trackingNumber", "SD-PROJECTION-TEST");
        event.put("pickupDistrict", "COLOMBO").put("deliveryDistrict", "KANDY");
        return event;
    }

    private ObjectNode assigned(UUID order, UUID courier, String at) {
        ObjectNode event = base();
        event.put("orderId", order.toString()).put("timestamp", at).put("assignedAt", at);
        event.put("courierId", courier.toString()).put("courierName", "Synthetic Test Courier");
        return event;
    }

    private ObjectNode changed(UUID order, String state, String at) {
        ObjectNode event = status();
        event.put("orderId", order.toString()).put("newPublicStatus", state).put("timestamp", at);
        return event;
    }

    private void apply(String key, ObjectNode event) {
        context.getBean(TelemetryProcessingService.class).process(
                context.getBean(TelemetryEventDecoder.class).decode(received(key, event)).orElseThrow());
    }

    private com.swiftdrop.analytics.service.AnalyticsReportService reports() {
        return context.getBean(com.swiftdrop.analytics.service.AnalyticsReportService.class);
    }

    private java.net.http.HttpResponse<String> reportHttp(String path, String role) throws Exception {
        var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:"
                + context.getEnvironment().getProperty("local.server.port") + "/api/v1/analytics" + path));
        if (role != null) request.header("X-User-Id", "00000000-0000-0000-0000-000000000001").header("X-User-Role", role);
        return java.net.http.HttpClient.newHttpClient().send(request.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void reportingHttpMatchesSqlWithInclusiveBoundariesAndZeroFilledDates() throws Exception {
        UUID c1 = UUID.randomUUID(), c2 = UUID.randomUUID();
        for (int i = 0; i < 4; i++) {
            UUID order = UUID.randomUUID();
            apply(CREATED, created(order, "2026-11-07T18:30:00Z")); // first instant of included day
            apply(ASSIGNED, assigned(order, i < 2 ? c1 : c2, "2026-11-08T06:10:00Z"));
            if (i < 3) apply(STATUS, changed(order, i < 2 ? "DELIVERED" : "FAILED", "2026-11-08T18:29:59Z"));
        }
        apply(CREATED, created(UUID.randomUUID(), "2026-11-07T18:29:59Z")); // excluded prior day
        apply(CREATED, created(UUID.randomUUID(), "2026-11-08T18:30:00Z")); // next day
        var daily = reportHttp("/deliveries/daily?from=2026-11-08&to=2026-11-10", "DISPATCHER");
        assertThat(daily.statusCode()).isEqualTo(200);
        var days = mapper.readTree(daily.body()).path("data").path("days");
        assertThat(days.size()).isEqualTo(3);
        assertThat(days.get(0).path("created").asLong()).isEqualTo(4);
        assertThat(days.get(0).path("delivered").asLong()).isEqualTo(2);
        assertThat(days.get(0).path("failed").asLong()).isEqualTo(1);
        assertThat(days.get(1).path("created").asLong()).isEqualTo(1);
        assertThat(days.get(2).path("created").asLong()).isZero();
        assertThat(jdbc.queryForObject("SELECT created FROM daily_delivery_stats WHERE report_date=?", Long.class, DAY)).isEqualTo(4);
        var rates = mapper.readTree(reportHttp("/success-rate?from=2026-11-08&to=2026-11-08", "SYSTEM_ADMIN").body()).path("data");
        assertThat(rates.path("completed").asLong()).isEqualTo(3);
        assertThat(rates.path("successRatePercent").decimalValue()).isEqualByComparingTo("66.67");
        var workload = mapper.readTree(reportHttp("/couriers/workload", "DISPATCHER").body()).path("data");
        assertThat(workload.path("totalActiveOrders").asLong()).isEqualTo(1);
        assertThat(workload.path("couriers").size()).isEqualTo(2);
        assertThat(reportHttp("/couriers/workload", "CUSTOMER").statusCode()).isEqualTo(403);
        assertThat(reportHttp("/couriers/workload", null).statusCode()).isEqualTo(401);
    }

    @Test
    void emptyReportingHttpAndLiveOpenApiMatchPublicContract() throws Exception {
        var response = reportHttp("/success-rate?from=2026-11-08&to=2026-11-08", "DISPATCHER");
        assertThat(response.statusCode()).isEqualTo(200);
        var data = mapper.readTree(response.body()).path("data");
        assertThat(data.has("successRatePercent")).isTrue();
        assertThat(data.path("successRatePercent").isNull()).isTrue();
        assertThat(data.path("failureRatePercent").isNull()).isTrue();
        var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:"
                + context.getEnvironment().getProperty("local.server.port") + "/v3/api-docs")).build();
        var docs = java.net.http.HttpClient.newHttpClient().send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(docs.statusCode()).isEqualTo(200);
        var schema = mapper.readTree(docs.body());
        assertThat(schema.path("paths").path("/api/v1/analytics/couriers/workload").path("get").path("security").toString())
                .contains("bearerAuth");
        assertThat(schema.path("paths").path("/api/v1/analytics/kpis").path("get").path("security").toString())
                .contains("bearerAuth");
        assertThat(reportHttp("/success-rate?from=2026-11-09&to=2026-11-08", "DISPATCHER").statusCode()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM daily_delivery_stats", Long.class)).isZero();
    }

    @Test
    void fourOrdersProduceExactDailyOutcomesAndCourierWorkload() {
        UUID c1 = UUID.randomUUID(), c2 = UUID.randomUUID();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID(), d = UUID.randomUUID();
        for (UUID id : java.util.List.of(a,b,c,d)) apply(CREATED, created(id, CREATED_AT));
        for (UUID id : java.util.List.of(a,b,d)) apply(ASSIGNED, assigned(id,c1,"2026-11-08T06:10:00Z"));
        apply(ASSIGNED, assigned(c,c2,"2026-11-08T06:10:00Z"));
        apply(ASSIGNED, assigned(d,c2,"2026-11-08T06:20:00Z"));
        ObjectNode delivered = changed(a,"DELIVERED","2026-11-08T07:00:00Z");
        apply(STATUS, delivered);
        apply(STATUS, delivered); // transport duplicate
        ObjectNode repeated = delivered.deepCopy();
        repeated.put("eventId", UUID.randomUUID().toString());
        apply(STATUS, repeated); // business duplicate with a distinct event ID
        apply(STATUS, changed(a,"IN_TRANSIT","2026-11-08T06:30:00Z")); // delayed older status
        apply(STATUS, changed(b,"DELIVERED","2026-11-08T08:00:00Z"));
        apply(STATUS, changed(c,"FAILED","2026-11-08T09:00:00Z"));
        apply(STATUS, changed(d,"IN_TRANSIT","2026-11-08T07:00:00Z"));
        var day = reports().daily(DAY,DAY).getFirst();
        assertThat(day.created()).isEqualTo(4);
        assertThat(day.delivered()).isEqualTo(2);
        assertThat(day.failed()).isEqualTo(1);
        assertThat(reports().outcomes(DAY,DAY).successRatePercent()).isEqualByComparingTo("66.67");
        assertThat(reports().workload().stream().filter(row -> row.courierId().equals(c1)).findFirst().orElseThrow().activeOrders()).isZero();
        assertThat(reports().workload().stream().filter(row -> row.courierId().equals(c2)).findFirst().orElseThrow().activeOrders()).isEqualTo(1);
        assertThat(reports().couriers(null,c1).getFirst().deliveredCount()).isEqualTo(2);
        assertThat(reports().couriers(null,c2).getFirst().failedCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT current_status FROM analytics_order_projection WHERE order_id=?",String.class,a)).isEqualTo("DELIVERED");
        apply(STATUS, changed(d,"DELIVERED","2026-11-08T10:00:00Z"));
        assertThat(reports().workload().stream().mapToLong(row -> row.activeOrders()).sum()).isZero();
    }

    @Test
    void delayedAssignmentsReconcileOutcomeCourierWithoutRecountingDelivery() {
        UUID order = UUID.randomUUID(), c1 = UUID.randomUUID(), c2 = UUID.randomUUID(), c3 = UUID.randomUUID();
        apply(STATUS, changed(order,"DELIVERED","2026-11-08T09:00:00Z"));
        assertThat(reports().workload()).isEmpty();
        apply(ASSIGNED, assigned(order,c1,"2026-11-08T06:00:00Z"));
        assertThat(reports().couriers(null,c1).getFirst().deliveredCount()).isEqualTo(1);
        apply(ASSIGNED, assigned(order,c2,"2026-11-08T08:00:00Z"));
        assertThat(reports().couriers(null,c1).getFirst().deliveredCount()).isZero();
        assertThat(reports().couriers(null,c2).getFirst().deliveredCount()).isEqualTo(1);
        apply(ASSIGNED, assigned(order,c3,"2026-11-08T10:00:00Z")); // after outcome, cannot steal credit/reopen
        apply(ASSIGNED, assigned(order,c1,"2026-11-08T07:00:00Z")); // older than reassignment
        assertThat(reports().couriers(null,c2).getFirst().deliveredCount()).isEqualTo(1);
        assertThat(reports().couriers(null,c3).getFirst().deliveredCount()).isZero();
        assertThat(reports().workload().stream().mapToLong(row -> row.activeOrders()).sum()).isZero();
        assertThat(reports().daily(DAY,DAY).getFirst().delivered()).isEqualTo(1);
        assertThat(reports().couriers(null,c1).getFirst().totalAssigned()).isEqualTo(1);
    }

    @Test
    void colomboMidnightAndNanosecondPrecisionDoNotMoveOrdersToWrongDay() {
        UUID before = UUID.randomUUID(), after = UUID.randomUUID();
        apply(CREATED, created(before,"2026-11-07T18:29:59.999999999Z"));
        apply(CREATED, created(after,"2026-11-07T18:30:00Z"));
        apply(STATUS, changed(before,"IN_TRANSIT","2026-11-08T06:00:00Z")); // recompute persisted state
        var days = reports().daily(DAY.minusDays(1),DAY);
        assertThat(days.getFirst().created()).isEqualTo(1);
        assertThat(days.getLast().created()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM analytics_order_projection",Integer.class)).isEqualTo(2);
    }

    @Test
    void cancellationsAndReturnsDoNotBecomeAdditionalFailures() {
        UUID cancelled = UUID.randomUUID(), returned = UUID.randomUUID(), failed = UUID.randomUUID();
        apply(STATUS, changed(cancelled,"CANCELLED","2026-11-08T07:00:00Z"));
        apply(STATUS, changed(returned,"RETURNED_TO_SENDER","2026-11-08T07:00:00Z"));
        apply(STATUS, changed(failed,"FAILED","2026-11-08T07:00:00Z"));
        apply(STATUS, changed(failed,"FAILED","2026-11-08T07:30:00Z"));
        apply(STATUS, changed(failed,"RETURNED_TO_SENDER","2026-11-08T08:00:00Z"));
        assertThat(reports().outcomes(DAY,DAY).completed()).isEqualTo(1);
        assertThat(reports().outcomes(DAY,DAY).failed()).isEqualTo(1);
        assertThat(reports().outcomes(DAY,DAY).successRatePercent()).isEqualByComparingTo("0.00");
    }

    @Test
    void conflictingOutcomesAreVisibleAndExcludedInsteadOfDoubleCounted() {
        UUID order = UUID.randomUUID();
        apply(STATUS, changed(order,"DELIVERED","2026-11-08T07:00:00Z"));
        assertThat(reports().outcomes(DAY,DAY).delivered()).isEqualTo(1);
        apply(STATUS, changed(order,"FAILED","2026-11-08T08:00:00Z"));
        assertThat(reports().outcomes(DAY,DAY).completed()).isZero();
        assertThat(jdbc.queryForObject("SELECT conflict_reason FROM analytics_order_projection",String.class)).isNotBlank();
        UUID tied = UUID.randomUUID();
        apply(STATUS, changed(tied,"DELIVERED","2026-11-08T09:00:00Z"));
        apply(STATUS, changed(tied,"FAILED","2026-11-08T09:00:00Z"));
        assertThat(reports().outcomes(DAY,DAY).completed()).isZero();
    }

    @Test
    void concurrentDifferentOrdersDoNotLoseDailyIncrements() throws Exception {
        try (var executor = Executors.newFixedThreadPool(8)) {
            var tasks = new ArrayList<java.util.concurrent.Future<?>>();
            for (int i=0; i<20; i++) {
                ObjectNode event = created(UUID.randomUUID(),CREATED_AT);
                tasks.add(executor.submit(() -> apply(CREATED,event)));
            }
            for (var task : tasks) task.get(30,TimeUnit.SECONDS);
        }
        assertThat(reports().daily(DAY,DAY).getFirst().created()).isEqualTo(20);
        assertThat(markers()).isEqualTo(20);
    }

    @Test
    void startupBackfillsPart2InboxAndDoesNotRepeatItOnNextRestart() throws Exception {
        UUID order = UUID.randomUUID(), courier = UUID.randomUUID();
        var decoder = context.getBean(TelemetryEventDecoder.class);
        var inbox = context.getBean(com.swiftdrop.analytics.domain.repository.ProcessedTelemetryEventRepository.class);
        for (var pair : java.util.Map.of(CREATED,created(order,CREATED_AT),
                ASSIGNED,assigned(order,courier,"2026-11-08T06:10:00Z"),
                STATUS,changed(order,"DELIVERED","2026-11-08T08:00:00Z")).entrySet()) {
            var event = decoder.decode(received(pair.getKey(),pair.getValue())).orElseThrow();
            inbox.register(event,mapper.writeValueAsString(event.payload())); // accepted by Part 2, not yet projected
        }
        assertThat(reports().daily(DAY,DAY).getFirst().created()).isZero();
        context.close(); startApplication();
        assertThat(reports().daily(DAY,DAY).getFirst().created()).isEqualTo(1);
        assertThat(reports().daily(DAY,DAY).getFirst().delivered()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM processed_telemetry_events WHERE NOT projection_applied",Integer.class)).isZero();
        context.close(); startApplication();
        assertThat(reports().daily(DAY,DAY).getFirst().delivered()).isEqualTo(1);
    }

    @Test
    void emptyReadsNeverSeedTablesOrReuseLegacySamples() {
        jdbc.update("INSERT INTO operational_sla_snapshots(district,total_parcels) VALUES ('COLOMBO',999)");
        var kpis = context.getBean(com.swiftdrop.analytics.service.SlaMetricsAggregationService.class).getSystemKpiSummary();
        assertThat(kpis.totalParcels()).isZero();
        assertThat(kpis.systemOnTimeRate()).isNull();
        assertThat(kpis.activeCouriers()).isZero();
        assertThat(reports().daily(DAY,DAY.plusDays(2))).hasSize(3).allMatch(row -> row.created()==0);
        assertThat(reports().outcomes(DAY,DAY).successRatePercent()).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM daily_delivery_stats",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM analytics_order_projection",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM operational_sla_snapshots",Integer.class)).isEqualTo(1);
    }

    @Test
    void reassignmentMovesCurrentWorkloadBetweenAssignmentDates() {
        UUID order = UUID.randomUUID(), c1 = UUID.randomUUID(), c2 = UUID.randomUUID();
        apply(ASSIGNED, assigned(order,c1,"2026-11-07T17:00:00Z"));
        assertThat(reports().workload().getFirst().activeOrders()).isEqualTo(1);
        apply(ASSIGNED, assigned(order,c2,"2026-11-08T06:00:00Z"));
        assertThat(jdbc.queryForObject("SELECT active FROM courier_daily_stats WHERE courier_id=? AND report_date=?",
                Long.class,c1,DAY.minusDays(1))).isZero();
        assertThat(jdbc.queryForObject("SELECT active FROM courier_daily_stats WHERE courier_id=? AND report_date=?",
                Long.class,c2,DAY)).isEqualTo(1);
        // Same effective time but different courier: do not pick either arbitrarily.
        apply(ASSIGNED, assigned(order,c1,"2026-11-08T06:00:00Z"));
        assertThat(reports().workload().stream().mapToLong(row -> row.activeOrders()).sum()).isZero();
        assertThat(jdbc.queryForObject("SELECT conflict_reason FROM analytics_order_projection",String.class))
                .isEqualTo("CONFLICTING_ASSIGNMENT_AT_SAME_TIME");
    }

    @Test
    void rangeRatesUseSummedOutcomesInsteadOfAverageDailyPercentages() {
        jdbc.update("INSERT INTO daily_delivery_stats(report_date,delivered,failed) VALUES (?,1,0),(?,1,8)",DAY,DAY.plusDays(1));
        assertThat(reports().outcomes(DAY,DAY.plusDays(1)).successRatePercent()).isEqualByComparingTo("20.00");
        assertThat(reports().daily(DAY,DAY.plusDays(2)).getLast().delivered()).isZero();
        assertThatThrownBy(() -> reports().daily(DAY,DAY.minusDays(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reports().daily(DAY,DAY.plusDays(90))).isInstanceOf(IllegalArgumentException.class);
        assertThat(reports().daily(DAY,DAY.plusDays(89))).hasSize(90);
    }

    @Test
    void creationAndOutcomeAreIndependentFlowsOnTheirOwnDates() {
        UUID order = UUID.randomUUID();
        apply(STATUS,changed(order,"CREATED","2026-11-07T06:00:00Z"));
        assertThat(reports().daily(DAY.minusDays(1),DAY).stream().mapToLong(row -> row.created()).sum()).isZero();
        apply(STATUS,changed(order,"DELIVERED","2026-11-08T06:00:00Z"));
        apply(CREATED,created(order,"2026-11-07T06:00:00Z"));
        var days = reports().daily(DAY.minusDays(1),DAY);
        assertThat(days.getFirst().created()).isEqualTo(1);
        assertThat(days.getFirst().delivered()).isZero();
        assertThat(days.getLast().created()).isZero();
        assertThat(days.getLast().delivered()).isEqualTo(1);
    }

    @Test
    void v3UpgradeRetainsInboxPayloadAndMarksItForCatchup() throws Exception {
        Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword())
                .schemas("upgrade_v3").defaultSchema("upgrade_v3").target("3").load().migrate();
        ObjectNode event = created(UUID.randomUUID(),CREATED_AT);
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword());
             var insert = connection.prepareStatement("INSERT INTO upgrade_v3.processed_telemetry_events "
                     + "(event_id,event_type,order_id,correlation_id,occurred_at,payload) VALUES (?,'order.created',?,?,?,CAST(? AS jsonb))")) {
            insert.setObject(1,UUID.fromString(event.get("eventId").asText()));
            insert.setObject(2,UUID.fromString(event.get("orderId").asText()));
            insert.setObject(3,UUID.fromString(event.get("correlationId").asText()));
            insert.setTimestamp(4,java.sql.Timestamp.from(Instant.parse(CREATED_AT)));
            insert.setString(5,event.toString());
            insert.executeUpdate();
            var upgrade = Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword())
                    .schemas("upgrade_v3").defaultSchema("upgrade_v3").load();
            assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
            upgrade.validate();
            try (var statement=connection.createStatement();
                 var result=statement.executeQuery("SELECT projection_applied,payload->>'orderId' FROM upgrade_v3.processed_telemetry_events")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getBoolean(1)).isFalse();
                assertThat(result.getString(2)).isEqualTo(event.get("orderId").asText());
            }
        }
    }
}
