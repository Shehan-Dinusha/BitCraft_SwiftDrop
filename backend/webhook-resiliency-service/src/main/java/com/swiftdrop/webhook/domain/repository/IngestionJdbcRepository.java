package com.swiftdrop.webhook.domain.repository;

import com.swiftdrop.webhook.domain.entity.InboundEventStatus;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * PostgreSQL-specific writes used by event ingestion.
 *
 * <p>These statements rely on {@code INSERT ... ON CONFLICT DO NOTHING} and advisory locks, which JPA
 * cannot express. They run on the caller's transaction: Spring's JPA transaction manager exposes its JDBC
 * connection to {@link NamedParameterJdbcTemplate}. Parameter SQL types are explicit because PostgreSQL
 * cannot infer the type of a {@code null} UUID bind.</p>
 *
 * <p>Every method must be called inside a transaction that has already taken {@link #lockOrder}.</p>
 */
@Repository
public class IngestionJdbcRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public IngestionJdbcRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Serialises ingestion work for one order across consumer threads and service replicas until the
     * transaction ends.
     *
     * <p>Without it, a status event and its {@code order.created} committing at the same time could each miss
     * the other's uncommitted row: the status event would park itself as awaiting a merchant after
     * {@code order.created} had already looked for parked events, and stay parked forever. Under READ COMMITTED,
     * every statement after the lock sees all work committed by the previous holder. A hash collision between
     * two orders only costs extra serialisation.</p>
     */
    public void lockOrder(UUID orderId) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))",
                new MapSqlParameterSource("key", "webhook-order:" + orderId),
                (RowCallbackHandler) rs -> { });
    }

    /** @return {@code true} if the event is new, {@code false} if this event id was already ingested */
    public boolean insertInboundEventIfAbsent(UUID eventId, String eventType, String schemaVersion,
                                              String sourceService, UUID correlationId, UUID orderId,
                                              String payloadJson, Instant occurredAt) {
        var params = new MapSqlParameterSource()
                .addValue("eventId", eventId, Types.OTHER)
                .addValue("eventType", eventType, Types.VARCHAR)
                .addValue("schemaVersion", schemaVersion, Types.VARCHAR)
                .addValue("sourceService", sourceService, Types.VARCHAR)
                .addValue("correlationId", correlationId, Types.OTHER)
                .addValue("orderId", orderId, Types.OTHER)
                .addValue("payload", payloadJson, Types.VARCHAR)
                .addValue("occurredAt", occurredAt.atOffset(ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("status", InboundEventStatus.AWAITING_MERCHANT.name(), Types.VARCHAR);
        return jdbc.update("""
                INSERT INTO inbound_events (event_id, event_type, schema_version, source_service, correlation_id,
                                            order_id, payload, occurred_at, processing_status)
                VALUES (:eventId, :eventType, :schemaVersion, :sourceService, :correlationId,
                        :orderId, CAST(:payload AS jsonb), :occurredAt, :status)
                ON CONFLICT (event_id) DO NOTHING
                """, params) == 1;
    }

    /**
     * Records the owner of an order unless one is already recorded. The caller compares the stored owner
     * with its claim afterwards, so a conflicting second claim is detected rather than silently ignored.
     */
    public void insertOrderOwnershipIfAbsent(UUID orderId, UUID merchantId, String trackingNumber,
                                             UUID sourceEventId) {
        var params = new MapSqlParameterSource()
                .addValue("orderId", orderId, Types.OTHER)
                .addValue("merchantId", merchantId, Types.OTHER)
                .addValue("trackingNumber", trackingNumber, Types.VARCHAR)
                .addValue("sourceEventId", sourceEventId, Types.OTHER);
        jdbc.update("""
                INSERT INTO order_merchant_map (order_id, merchant_id, tracking_number, source_event_id)
                VALUES (:orderId, :merchantId, :trackingNumber, :sourceEventId)
                ON CONFLICT (order_id) DO NOTHING
                """, params);
    }

    /** Parked events for an order, oldest first, row-locked for resolution. */
    public List<AwaitingEvent> findAwaitingEventsForUpdate(UUID orderId) {
        return jdbc.query("""
                SELECT event_id, event_type
                FROM inbound_events
                WHERE order_id = :orderId AND processing_status = 'AWAITING_MERCHANT'
                ORDER BY occurred_at, received_at
                FOR UPDATE
                """, new MapSqlParameterSource().addValue("orderId", orderId, Types.OTHER),
                (rs, row) -> new AwaitingEvent(rs.getObject("event_id", UUID.class), rs.getString("event_type")));
    }

    /** Moves an awaiting event to {@code RESOLVED}. Must precede inserting its deliveries (composite foreign key). */
    public void markResolved(UUID eventId, UUID merchantId) {
        int updated = jdbc.update("""
                UPDATE inbound_events
                SET processing_status = 'RESOLVED', merchant_id = :merchantId, resolved_at = now()
                WHERE event_id = :eventId AND processing_status = 'AWAITING_MERCHANT'
                """, new MapSqlParameterSource()
                .addValue("eventId", eventId, Types.OTHER)
                .addValue("merchantId", merchantId, Types.OTHER));
        requireSingleTransition(updated, eventId);
    }

    public void markNoMerchant(UUID eventId) {
        int updated = jdbc.update("""
                UPDATE inbound_events
                SET processing_status = 'NO_MERCHANT', resolved_at = now()
                WHERE event_id = :eventId AND processing_status = 'AWAITING_MERCHANT'
                """, new MapSqlParameterSource().addValue("eventId", eventId, Types.OTHER));
        requireSingleTransition(updated, eventId);
    }

    /**
     * Creates a {@code PENDING} delivery, due immediately, unless an original delivery for this event and
     * endpoint already exists. The partial unique index is the final guard against concurrent duplicates.
     *
     * @return {@code true} if a delivery was created
     */
    public boolean insertDeliveryIfAbsent(UUID deliveryId, UUID eventId, UUID endpointId, UUID merchantId) {
        var params = new MapSqlParameterSource()
                .addValue("deliveryId", deliveryId, Types.OTHER)
                .addValue("eventId", eventId, Types.OTHER)
                .addValue("endpointId", endpointId, Types.OTHER)
                .addValue("merchantId", merchantId, Types.OTHER);
        return jdbc.update("""
                INSERT INTO webhook_deliveries (delivery_id, inbound_event_id, endpoint_id, merchant_id,
                                                status, attempt_count, next_attempt_at)
                VALUES (:deliveryId, :eventId, :endpointId, :merchantId, 'PENDING', 0, now())
                ON CONFLICT (inbound_event_id, endpoint_id) WHERE replay_of_delivery_id IS NULL DO NOTHING
                """, params) == 1;
    }

    private static void requireSingleTransition(int updated, UUID eventId) {
        // The order lock makes this unreachable unless the row was changed outside ingestion.
        if (updated != 1) {
            throw new IllegalStateException("Inbound event " + eventId + " was not awaiting a merchant");
        }
    }

    public record AwaitingEvent(UUID eventId, String eventType) {
    }
}
