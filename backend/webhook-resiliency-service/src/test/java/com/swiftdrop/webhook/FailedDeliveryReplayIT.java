package com.swiftdrop.webhook;

import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.security.CallerContext;
import com.swiftdrop.contracts.security.ForbiddenException;
import com.swiftdrop.webhook.domain.entity.AttemptErrorClass;
import com.swiftdrop.webhook.domain.entity.WebhookDeliveryStatus;
import com.swiftdrop.webhook.domain.repository.DeliveryJobRepository;
import com.swiftdrop.webhook.domain.repository.FailedDeliveryRepository;
import com.swiftdrop.webhook.exception.FailedDeliveryNotFoundException;
import com.swiftdrop.webhook.service.FailedDeliveryService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Failed deliveries and replay as real SQL on PostgreSQL. No broker and no HTTP. */
@Testcontainers
class FailedDeliveryReplayIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static JdbcTemplate jdbc;
    private static FailedDeliveryService service;
    private static DeliveryJobRepository jobs;

    private final UUID merchant = UUID.randomUUID();
    private final UUID otherMerchant = UUID.randomUUID();
    private final CallerContext owner = new CallerContext(UUID.randomUUID(), UserRole.MERCHANT, merchant, null);
    private final CallerContext stranger = new CallerContext(UUID.randomUUID(), UserRole.MERCHANT, otherMerchant, null);

    @BeforeAll
    static void migrate() {
        var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbc = new JdbcTemplate(dataSource);
        var named = new NamedParameterJdbcTemplate(dataSource);
        service = new FailedDeliveryService(new FailedDeliveryRepository(named));
        jobs = new DeliveryJobRepository(named);
    }

    @BeforeEach
    void clear() {
        jdbc.execute("TRUNCATE webhook_delivery_attempts, webhook_deliveries, order_merchant_map, inbound_events, webhook_endpoints CASCADE");
    }

    /** One event of {@code owner}'s order whose delivery ended with {@code status}. */
    private UUID delivery(UUID ownerId, String status) {
        UUID event = UUID.randomUUID(), order = UUID.randomUUID(), endpoint = UUID.randomUUID(), delivery = UUID.randomUUID();
        jdbc.update("INSERT INTO webhook_endpoints(endpoint_id, merchant_id, target_url, secret_key) VALUES (?,?,?,?)",
                endpoint, ownerId, "https://shop.example.com/hook", "whsec_x");
        jdbc.update("""
                INSERT INTO inbound_events(event_id, event_type, schema_version, correlation_id, order_id, payload,
                    occurred_at, processing_status, merchant_id, resolved_at)
                VALUES (?, 'parcel.status.changed', '1', ?, ?, '{}'::jsonb, now(), 'RESOLVED', ?, now())
                """, event, UUID.randomUUID(), order, ownerId);
        jdbc.update("INSERT INTO order_merchant_map(order_id, merchant_id, tracking_number, source_event_id) VALUES (?,?,?,?)",
                order, ownerId, "SD-AAAA-BBBB", event);
        jdbc.update("INSERT INTO webhook_deliveries(delivery_id, inbound_event_id, endpoint_id, merchant_id, status, attempt_count) VALUES (?,?,?,?,?,4)",
                delivery, event, endpoint, ownerId, status);
        jdbc.update("""
                INSERT INTO webhook_delivery_attempts(attempt_id, delivery_id, endpoint_id, merchant_id, event_type,
                    http_status_code, attempt_number, is_success, error_class)
                VALUES (?, ?, ?, ?, 'parcel.status.changed', 503, 4, FALSE, 'HTTP_SERVER_ERROR')
                """, UUID.randomUUID(), delivery, endpoint, ownerId);
        return delivery;
    }

    private int replays(UUID original) {
        return jdbc.queryForObject("SELECT count(*) FROM webhook_deliveries WHERE replay_of_delivery_id = ?", Integer.class, original);
    }

    private void endReplay(UUID original, String status) {
        jdbc.update("UPDATE webhook_deliveries SET status = ?, next_attempt_at = NULL WHERE replay_of_delivery_id = ? AND status = 'PENDING'",
                status, original);
    }

    @Test
    void listsOnlyTheCallersFailedDeliveriesWithTheReason() {
        UUID failed = delivery(merchant, "DEAD");
        delivery(merchant, "SUCCEEDED");
        delivery(otherMerchant, "DEAD");

        assertThat(service.list(owner)).singleElement().satisfies(row -> {
            assertThat(row.deliveryId()).isEqualTo(failed);
            assertThat(row.trackingNumber()).isEqualTo("SD-AAAA-BBBB");
            assertThat(row.lastHttpStatus()).isEqualTo(503);
            assertThat(row.errorClass()).isEqualTo(AttemptErrorClass.HTTP_SERVER_ERROR);
            assertThat(row.attempts()).isEqualTo(4);
            assertThat(row.replayStatus()).isNull();
        });
        assertThat(service.list(new CallerContext(UUID.randomUUID(), UserRole.SYSTEM_ADMIN, null, null))).hasSize(2);
    }

    @Test
    void aReplayIsOneNewJobForTheSameEventHoweverOftenItIsAsked() {
        UUID failed = delivery(merchant, "DEAD");

        assertThat(service.replay(failed, owner).replayStatus()).isEqualTo(WebhookDeliveryStatus.PENDING);
        service.replay(failed, owner);
        assertThat(replays(failed)).isEqualTo(1);

        var claimed = jobs.claimDue("worker", Duration.ofSeconds(60), 10, 4);
        assertThat(claimed).singleElement().satisfies(job -> {
            assertThat(job.deliveryId()).isNotEqualTo(failed);
            assertThat(job.inboundEventId()).isEqualTo(jdbc.queryForObject(
                    "SELECT inbound_event_id FROM webhook_deliveries WHERE delivery_id = ?", UUID.class, failed));
        });
    }

    @Test
    void aFailedReplayCanBeTriedAgainAndADeliveredOneCannot() {
        UUID failed = delivery(merchant, "DEAD");
        service.replay(failed, owner);
        endReplay(failed, "DEAD");
        // The dead replay is not a second entry in the list.
        assertThat(service.list(owner)).singleElement()
                .satisfies(row -> assertThat(row.replayStatus()).isEqualTo(WebhookDeliveryStatus.DEAD));

        service.replay(failed, owner);
        assertThat(replays(failed)).isEqualTo(2);
        endReplay(failed, "SUCCEEDED");

        assertThat(service.replay(failed, owner).replayStatus()).isEqualTo(WebhookDeliveryStatus.SUCCEEDED);
        assertThat(replays(failed)).isEqualTo(2);
    }

    @Test
    void replayAllTakesOnlyTheCallersUndelivered() {
        UUID first = delivery(merchant, "DEAD");
        UUID delivered = delivery(merchant, "DEAD");
        UUID theirs = delivery(otherMerchant, "DEAD");
        service.replay(delivered, owner);
        endReplay(delivered, "SUCCEEDED");

        assertThat(service.replayAll(owner)).isEqualTo(1);
        assertThat(replays(first)).isEqualTo(1);
        assertThat(replays(delivered)).isEqualTo(1);
        assertThat(replays(theirs)).isZero();
        assertThat(service.replayAll(owner)).isZero();
    }

    @Test
    void anotherMerchantsDeliveryIsNotFoundAndOtherRolesAreRefused() {
        UUID failed = delivery(merchant, "DEAD");

        assertThatThrownBy(() -> service.replay(failed, stranger)).isInstanceOf(FailedDeliveryNotFoundException.class);
        assertThat(replays(failed)).isZero();
        var dispatcher = new CallerContext(UUID.randomUUID(), UserRole.DISPATCHER, null, null);
        assertThatThrownBy(() -> service.list(dispatcher)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.replayAll(dispatcher)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void theDatabaseRefusesTwoActiveReplays() {
        UUID failed = delivery(merchant, "DEAD");
        service.replay(failed, owner);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO webhook_deliveries(delivery_id, inbound_event_id, endpoint_id, merchant_id, status,
                    attempt_count, next_attempt_at, replay_of_delivery_id)
                SELECT gen_random_uuid(), inbound_event_id, endpoint_id, merchant_id, 'PENDING', 0, now(), delivery_id
                FROM webhook_deliveries WHERE delivery_id = ?
                """, failed)).isInstanceOf(DuplicateKeyException.class);
    }
}
