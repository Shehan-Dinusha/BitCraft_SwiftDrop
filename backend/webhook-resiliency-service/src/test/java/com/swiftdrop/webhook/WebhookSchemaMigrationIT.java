package com.swiftdrop.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.webhook.domain.entity.InboundEvent;
import com.swiftdrop.webhook.domain.entity.InboundEventStatus;
import com.swiftdrop.webhook.domain.entity.WebhookDelivery;
import com.swiftdrop.webhook.domain.entity.WebhookDeliveryStatus;
import com.swiftdrop.webhook.domain.entity.WebhookEndpoint;
import com.swiftdrop.webhook.domain.repository.InboundEventRepository;
import com.swiftdrop.webhook.domain.repository.IngestionJdbcRepository;
import com.swiftdrop.webhook.domain.repository.OrderMerchantMappingRepository;
import com.swiftdrop.webhook.domain.repository.WebhookDeliveryRepository;
import com.swiftdrop.webhook.domain.repository.WebhookEndpointRepository;
import com.swiftdrop.webhook.dto.DeadLetterWebhookResponse;
import com.swiftdrop.webhook.dto.WebhookDeliveryAttemptResponse;
import com.swiftdrop.webhook.service.DlqReplayService;
import com.swiftdrop.webhook.service.WebhookSubscriptionService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 1: schema, constraints and the upgrade of an existing V2 database. Runs against real PostgreSQL because
 * JSONB, ON CONFLICT, partial unique indexes and composite foreign keys are PostgreSQL behaviour.
 * Requires Docker: the build must fail rather than silently skip this evidence.
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WebhookSchemaMigrationIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final UUID LEGACY_MERCHANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID LEGACY_ENDPOINT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID LEGACY_ATTEMPT = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID LEGACY_DLQ = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private ConfigurableApplicationContext context;
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private IngestionJdbcRepository ingestion;

    @BeforeAll
    void upgradeExistingV2DatabaseThenStartApplication() {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .target("2").load().migrate();
        var legacy = new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        legacy.update("""
                INSERT INTO webhook_endpoints (endpoint_id, merchant_id, target_url, secret_key, subscribed_events)
                VALUES (?, ?, 'https://legacy.example.com/hook', 'whsec_legacy', 'parcel.status.changed')
                """, LEGACY_ENDPOINT, LEGACY_MERCHANT);
        legacy.update("""
                INSERT INTO webhook_delivery_attempts (attempt_id, endpoint_id, merchant_id, event_type, payload,
                                                       http_status_code, attempt_number, is_success, error_message)
                VALUES (?, ?, ?, 'parcel.status.changed', '{"legacy":true}', 500, 3, false, 'Connection refused')
                """, LEGACY_ATTEMPT, LEGACY_ENDPOINT, LEGACY_MERCHANT);
        legacy.update("""
                INSERT INTO dead_letter_webhook_records (dlq_id, endpoint_id, merchant_id, event_type, payload, failure_reason)
                VALUES (?, ?, ?, 'parcel.status.changed', '{"legacy":true}', 'Connection refused')
                """, LEGACY_DLQ, LEGACY_ENDPOINT, LEGACY_MERCHANT);

        // Starting the real application applies V3 through Flyway and then validates every entity mapping.
        context = new SpringApplicationBuilder(WebhookResiliencyApplication.class).web(WebApplicationType.NONE).run(
                "--spring.datasource.url=" + postgres.getJdbcUrl(),
                "--spring.datasource.username=" + postgres.getUsername(),
                "--spring.datasource.password=" + postgres.getPassword(),
                "--spring.rabbitmq.listener.simple.auto-startup=false",
                "--spring.main.banner-mode=off",
                "--logging.level.root=WARN");
        jdbc = context.getBean(JdbcTemplate.class);
        tx = context.getBean(TransactionTemplate.class);
        ingestion = context.getBean(IngestionJdbcRepository.class);
    }

    @AfterAll
    void stop() {
        if (context != null) {
            context.close();
        }
    }

    // --- Upgrade of existing data ---------------------------------------------------------------------------------

    @Test
    void upgradeAppliesV3AndKeepsLegacyHistoryReadableThroughExistingApis() {
        assertThat(jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank",
                String.class)).containsExactly("1", "2", "3");

        List<DeadLetterWebhookResponse> dlq = context.getBean(DlqReplayService.class).listDlqRecords(LEGACY_MERCHANT, false);
        assertThat(dlq).singleElement().satisfies(record -> {
            assertThat(record.dlqId()).isEqualTo(LEGACY_DLQ);
            assertThat(record.payload()).isEqualTo("{\"legacy\":true}");
        });

        List<WebhookDeliveryAttemptResponse> attempts =
                context.getBean(WebhookSubscriptionService.class).getDeliveryAttempts(LEGACY_ENDPOINT);
        assertThat(attempts).singleElement().satisfies(attempt -> {
            assertThat(attempt.attemptId()).isEqualTo(LEGACY_ATTEMPT);
            assertThat(attempt.attemptNumber()).isEqualTo(3);
        });
        // Legacy attempts are not linked to a delivery job and keep their own payload copy.
        assertThat(jdbc.queryForMap("SELECT delivery_id, payload FROM webhook_delivery_attempts WHERE attempt_id = ?",
                LEGACY_ATTEMPT)).containsEntry("delivery_id", null).containsEntry("payload", "{\"legacy\":true}");
    }

    @Test
    void placeholderEndpointDefaultsWereRemoved() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO webhook_endpoints (endpoint_id) VALUES (?)", UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // --- Inbox -----------------------------------------------------------------------------------------------------

    @Test
    void sourceEventIdIsUniqueAndConflictSafeInsertReportsDuplicates() {
        UUID eventId = UUID.randomUUID();
        assertThat(insertEvent(eventId, UUID.randomUUID(), "{\"a\":1}")).isTrue();
        assertThat(insertEvent(eventId, UUID.randomUUID(), "{\"a\":2}")).isFalse();
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO inbound_events (event_id, event_type, schema_version, correlation_id, payload, occurred_at,
                                            processing_status)
                VALUES (?, 'order.created', '1', ?, '{}'::jsonb, now(), 'AWAITING_MERCHANT')
                """, eventId, UUID.randomUUID())).isInstanceOf(DuplicateKeyException.class);
        assertThat(jdbc.queryForObject("SELECT payload->>'a' FROM inbound_events WHERE event_id = ?", String.class, eventId))
                .isEqualTo("1");
    }

    @Test
    void jsonbPayloadRoundTripsThroughTheEntityAndIsQueryable() throws Exception {
        UUID eventId = UUID.randomUUID();
        String json = "{\"orderId\":\"o-1\",\"newPublicStatus\":\"DELIVERED\",\"nested\":{\"n\":[1,2,3]}}";
        insertEvent(eventId, UUID.randomUUID(), json);

        InboundEvent stored = context.getBean(InboundEventRepository.class).findById(eventId).orElseThrow();
        assertThat(stored.getPayload()).isEqualTo(new ObjectMapper().readTree(json));
        assertThat(stored.getProcessingStatus()).isEqualTo(InboundEventStatus.AWAITING_MERCHANT);
        assertThat(jdbc.queryForObject("SELECT pg_typeof(payload)::text FROM inbound_events WHERE event_id = ?",
                String.class, eventId)).isEqualTo("jsonb");
        assertThat(jdbc.queryForObject("SELECT payload #>> '{nested,n,2}' FROM inbound_events WHERE event_id = ?",
                String.class, eventId)).isEqualTo("3");
    }

    @Test
    void inboxRejectsNonObjectPayloadAndInconsistentResolution() {
        assertThatThrownBy(() -> insertEvent(UUID.randomUUID(), UUID.randomUUID(), "[1,2]"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO inbound_events (event_id, event_type, schema_version, correlation_id, payload, occurred_at,
                                            processing_status, resolved_at)
                VALUES (?, 'order.created', '1', ?, '{}'::jsonb, now(), 'RESOLVED', now())
                """, UUID.randomUUID(), UUID.randomUUID()))
                .as("RESOLVED requires a merchant").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO inbound_events (event_id, event_type, schema_version, correlation_id, payload, occurred_at,
                                            processing_status)
                VALUES (?, 'order.created', '1', ?, '{}'::jsonb, now(), 'SOMETHING_ELSE')
                """, UUID.randomUUID(), UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
    }

    // --- Order ownership -------------------------------------------------------------------------------------------

    @Test
    void orderIdIsUniqueAndOneMerchantOwnsManyOrders() {
        UUID merchant = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        tx.executeWithoutResult(status -> {
            UUID e1 = UUID.randomUUID();
            UUID e2 = UUID.randomUUID();
            insertEvent(e1, first, "{}");
            insertEvent(e2, second, "{}");
            ingestion.insertOrderOwnershipIfAbsent(first, merchant, "SD-1", e1);
            ingestion.insertOrderOwnershipIfAbsent(second, merchant, "SD-2", e2);
            // Second claim on the same order is ignored by the conflict-safe insert...
            ingestion.insertOrderOwnershipIfAbsent(first, UUID.randomUUID(), "SD-X", e2);
        });
        assertThat(context.getBean(OrderMerchantMappingRepository.class).findByMerchantId(merchant)).hasSize(2);
        assertThat(context.getBean(OrderMerchantMappingRepository.class).findById(first).orElseThrow().getMerchantId())
                .isEqualTo(merchant);
        // ...and rejected by the primary key when inserted directly.
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO order_merchant_map (order_id, merchant_id, source_event_id) SELECT ?, ?, event_id FROM inbound_events LIMIT 1",
                first, UUID.randomUUID())).isInstanceOf(DuplicateKeyException.class);
    }

    // --- Deliveries ------------------------------------------------------------------------------------------------

    @Test
    void oneOriginalDeliveryPerEventAndEndpointWhileReplaysRemainPossible() {
        UUID merchant = UUID.randomUUID();
        WebhookEndpoint endpoint = endpoint(merchant);
        UUID eventId = resolvedEvent(merchant);
        UUID first = UUID.randomUUID();

        Boolean created = tx.execute(s -> ingestion.insertDeliveryIfAbsent(first, eventId, endpoint.getEndpointId(), merchant));
        Boolean duplicate = tx.execute(s -> ingestion.insertDeliveryIfAbsent(UUID.randomUUID(), eventId, endpoint.getEndpointId(), merchant));
        assertThat(created).isTrue();
        assertThat(duplicate).isFalse();
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO webhook_deliveries (delivery_id, inbound_event_id, endpoint_id, merchant_id, next_attempt_at)
                VALUES (?, ?, ?, ?, now())
                """, UUID.randomUUID(), eventId, endpoint.getEndpointId(), merchant)).isInstanceOf(DuplicateKeyException.class);

        // A replay is a new job pointing at the original.
        jdbc.update("""
                INSERT INTO webhook_deliveries (delivery_id, inbound_event_id, endpoint_id, merchant_id, next_attempt_at,
                                                replay_of_delivery_id)
                VALUES (?, ?, ?, ?, now(), ?)
                """, UUID.randomUUID(), eventId, endpoint.getEndpointId(), merchant, first);

        List<WebhookDelivery> deliveries = context.getBean(WebhookDeliveryRepository.class).findByInboundEventEventId(eventId);
        assertThat(deliveries).hasSize(2);
        WebhookDelivery original = deliveries.stream().filter(d -> d.getDeliveryId().equals(first)).findFirst().orElseThrow();
        assertThat(original.getStatus()).isEqualTo(WebhookDeliveryStatus.PENDING);
        assertThat(original.getAttemptCount()).isZero();
        assertThat(original.getNextAttemptAt()).isNotNull();
        assertThat(original.getVersion()).isZero();
        assertThat(original.getReplayOfDeliveryId()).isNull();
    }

    @Test
    void databaseRejectsDeliveriesThatCrossMerchants() {
        UUID merchantA = UUID.randomUUID();
        UUID merchantB = UUID.randomUUID();
        WebhookEndpoint endpointB = endpoint(merchantB);
        UUID eventOfA = resolvedEvent(merchantA);

        // Delivery claims merchant A but targets merchant B's endpoint.
        assertThatThrownBy(() -> tx.execute(s -> ingestion.insertDeliveryIfAbsent(
                UUID.randomUUID(), eventOfA, endpointB.getEndpointId(), merchantA)))
                .isInstanceOf(DataIntegrityViolationException.class);
        // Delivery claims merchant B (matching the endpoint) for an event resolved to merchant A.
        assertThatThrownBy(() -> tx.execute(s -> ingestion.insertDeliveryIfAbsent(
                UUID.randomUUID(), eventOfA, endpointB.getEndpointId(), merchantB)))
                .isInstanceOf(DataIntegrityViolationException.class);
        // An unresolved event cannot have deliveries at all.
        UUID unresolved = UUID.randomUUID();
        insertEvent(unresolved, UUID.randomUUID(), "{}");
        assertThatThrownBy(() -> tx.execute(s -> ingestion.insertDeliveryIfAbsent(
                UUID.randomUUID(), unresolved, endpointB.getEndpointId(), merchantB)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deliveryStateConstraintsHold() {
        UUID merchant = UUID.randomUUID();
        WebhookEndpoint endpoint = endpoint(merchant);
        UUID eventId = resolvedEvent(merchant);
        String insert = """
                INSERT INTO webhook_deliveries (delivery_id, inbound_event_id, endpoint_id, merchant_id, status,
                                                next_attempt_at, lease_owner, lease_expires_at, attempt_count)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        Object now = java.sql.Timestamp.from(Instant.now());
        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), eventId, endpoint.getEndpointId(), merchant,
                "IN_FLIGHT", now, null, null, 0)).as("IN_FLIGHT needs a lease").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), eventId, endpoint.getEndpointId(), merchant,
                "SUCCEEDED", null, "worker-1", now, 1)).as("lease only while IN_FLIGHT").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), eventId, endpoint.getEndpointId(), merchant,
                "RETRY", null, null, null, 1)).as("RETRY needs a schedule").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), eventId, endpoint.getEndpointId(), merchant,
                "LOST", now, null, null, 0)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), eventId, endpoint.getEndpointId(), merchant,
                "PENDING", now, null, null, -1)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void jobAttemptsReferenceTheirDeliveryWithoutCopyingThePayload() {
        UUID merchant = UUID.randomUUID();
        WebhookEndpoint endpoint = endpoint(merchant);
        UUID eventId = resolvedEvent(merchant);
        UUID deliveryId = UUID.randomUUID();
        tx.executeWithoutResult(s -> ingestion.insertDeliveryIfAbsent(deliveryId, eventId, endpoint.getEndpointId(), merchant));
        String insert = """
                INSERT INTO webhook_delivery_attempts (attempt_id, delivery_id, endpoint_id, merchant_id, event_type,
                                                       payload, attempt_number, error_class, latency_ms)
                VALUES (?, ?, ?, ?, 'parcel.status.changed', ?, ?, ?, ?)
                """;
        jdbc.update(insert, UUID.randomUUID(), deliveryId, endpoint.getEndpointId(), merchant, null, 1, "TIMEOUT", 10_000);

        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), deliveryId, endpoint.getEndpointId(), merchant,
                null, 1, null, 5)).as("attempt number unique per delivery").isInstanceOf(DuplicateKeyException.class);
        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), deliveryId, endpoint.getEndpointId(), merchant,
                "{}", 2, null, 5)).as("no payload copy on job attempts").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), null, endpoint.getEndpointId(), merchant,
                null, 1, null, 5)).as("legacy shape needs its payload").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), deliveryId, endpoint.getEndpointId(), merchant,
                null, 3, "TEAPOT", 5)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), deliveryId, endpoint.getEndpointId(), merchant,
                null, 4, null, -1)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), deliveryId, endpoint.getEndpointId(), merchant,
                null, 0, null, 1)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void endpointWithDeliveryHistoryCannotBeHardDeletedButOthersCan() {
        UUID merchant = UUID.randomUUID();
        WebhookEndpoint used = endpoint(merchant);
        WebhookEndpoint unused = endpoint(merchant);
        UUID eventId = resolvedEvent(merchant);
        tx.executeWithoutResult(s -> ingestion.insertDeliveryIfAbsent(UUID.randomUUID(), eventId, used.getEndpointId(), merchant));

        var subscriptions = context.getBean(WebhookSubscriptionService.class);
        assertThatThrownBy(() -> subscriptions.deleteEndpoint(used.getEndpointId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        subscriptions.deleteEndpoint(unused.getEndpointId());
        var endpoints = context.getBean(WebhookEndpointRepository.class);
        assertThat(endpoints.findByEndpointId(used.getEndpointId())).isPresent();
        assertThat(endpoints.findByEndpointId(unused.getEndpointId())).isEmpty();
    }

    // --- helpers ---------------------------------------------------------------------------------------------------

    private boolean insertEvent(UUID eventId, UUID orderId, String payload) {
        return Boolean.TRUE.equals(tx.execute(s -> ingestion.insertInboundEventIfAbsent(eventId,
                "parcel.status.changed", "1", null, UUID.randomUUID(), orderId, payload, Instant.now())));
    }

    private UUID resolvedEvent(UUID merchant) {
        UUID eventId = UUID.randomUUID();
        tx.executeWithoutResult(s -> {
            ingestion.insertInboundEventIfAbsent(eventId, "parcel.status.changed", "1", null, UUID.randomUUID(),
                    UUID.randomUUID(), "{}", Instant.now());
            ingestion.markResolved(eventId, merchant);
        });
        return eventId;
    }

    private WebhookEndpoint endpoint(UUID merchant) {
        return context.getBean(WebhookEndpointRepository.class).save(WebhookEndpoint.builder()
                .merchantId(merchant)
                .targetUrl("https://merchant.example.com/hook")
                .secretKey("whsec_test")
                .subscribedEvents("parcel.status.changed")
                .build());
    }
}
