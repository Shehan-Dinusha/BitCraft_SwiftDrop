package com.swiftdrop.webhook.domain.repository;

import com.swiftdrop.webhook.domain.entity.AttemptErrorClass;
import com.swiftdrop.webhook.domain.entity.WebhookDeliveryStatus;
import com.swiftdrop.webhook.dto.FailedDeliveryResponse;
import com.swiftdrop.webhook.service.DlqFailureClassifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Deliveries that failed for good ({@code DEAD} originals) and their replays. A replay is a new row for the same
 * event and endpoint, which the delivery worker sends like any other job, so the merchant receives the same
 * event id with a new delivery id. {@code merchantId} is the tenant filter; null means every merchant and is for
 * administrators only (the service decides).
 */
@Repository
public class FailedDeliveryRepository {

    private static final String SELECT = """
            SELECT d.delivery_id, d.endpoint_id, d.attempt_count, d.updated_at,
                   ev.event_id, ev.event_type, ev.order_id, ev.occurred_at, map.tracking_number,
                   last.http_status_code, last.error_class,
                   replay.status AS replay_status, replay.created_at AS replayed_at
            FROM webhook_deliveries d
            JOIN inbound_events ev ON ev.event_id = d.inbound_event_id
            LEFT JOIN order_merchant_map map ON map.order_id = ev.order_id
            LEFT JOIN LATERAL (
                SELECT a.http_status_code, a.error_class
                FROM webhook_delivery_attempts a
                WHERE a.delivery_id = d.delivery_id
                ORDER BY a.attempt_number DESC LIMIT 1) last ON TRUE
            LEFT JOIN LATERAL (
                SELECT r.status, r.created_at
                FROM webhook_deliveries r
                WHERE r.replay_of_delivery_id = d.delivery_id
                ORDER BY r.created_at DESC LIMIT 1) replay ON TRUE
            WHERE d.status = 'DEAD'
              AND d.replay_of_delivery_id IS NULL
              AND (CAST(:merchantId AS uuid) IS NULL OR d.merchant_id = CAST(:merchantId AS uuid))
            """;

    private static final RowMapper<FailedDeliveryResponse> ROW = FailedDeliveryRepository::map;

    private final NamedParameterJdbcTemplate jdbc;

    public FailedDeliveryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Newest first. */
    public List<FailedDeliveryResponse> list(UUID merchantId, int limit) {
        return jdbc.query(SELECT + " ORDER BY d.updated_at DESC, d.delivery_id LIMIT :limit",
                tenant(merchantId).addValue("limit", limit, Types.INTEGER), ROW);
    }

    public Optional<FailedDeliveryResponse> find(UUID deliveryId, UUID merchantId) {
        return jdbc.query(SELECT + " AND d.delivery_id = :deliveryId",
                tenant(merchantId).addValue("deliveryId", deliveryId, Types.OTHER), ROW).stream().findFirst();
    }

    /**
     * Queues one replay for each failed delivery that has none waiting, running or delivered: all of them, or only
     * {@code deliveryId}. The partial unique index is the final guard against two replays at once.
     *
     * @return how many were queued
     */
    public int queueReplays(UUID merchantId, UUID deliveryId) {
        return jdbc.update("""
                INSERT INTO webhook_deliveries (delivery_id, inbound_event_id, endpoint_id, merchant_id, status,
                                                attempt_count, next_attempt_at, replay_of_delivery_id)
                SELECT gen_random_uuid(), d.inbound_event_id, d.endpoint_id, d.merchant_id, 'PENDING',
                       0, now(), d.delivery_id
                FROM webhook_deliveries d
                WHERE d.status = 'DEAD'
                  AND d.replay_of_delivery_id IS NULL
                  AND (CAST(:merchantId AS uuid) IS NULL OR d.merchant_id = CAST(:merchantId AS uuid))
                  AND (CAST(:deliveryId AS uuid) IS NULL OR d.delivery_id = CAST(:deliveryId AS uuid))
                  AND NOT EXISTS (
                      SELECT 1 FROM webhook_deliveries r
                      WHERE r.replay_of_delivery_id = d.delivery_id AND r.status <> 'DEAD')
                ON CONFLICT (replay_of_delivery_id)
                    WHERE replay_of_delivery_id IS NOT NULL AND status IN ('PENDING', 'IN_FLIGHT', 'RETRY')
                    DO NOTHING
                """, tenant(merchantId).addValue("deliveryId", deliveryId, Types.OTHER));
    }

    private static MapSqlParameterSource tenant(UUID merchantId) {
        return new MapSqlParameterSource().addValue("merchantId", merchantId, Types.OTHER);
    }

    private static FailedDeliveryResponse map(ResultSet rs, int row) throws SQLException {
        String storedClass = rs.getString("error_class");
        AttemptErrorClass errorClass = storedClass == null ? AttemptErrorClass.UNKNOWN : AttemptErrorClass.valueOf(storedClass);
        Integer httpStatus = rs.getObject("http_status_code", Integer.class);
        String replayStatus = rs.getString("replay_status");
        return new FailedDeliveryResponse(
                rs.getObject("delivery_id", UUID.class),
                rs.getObject("endpoint_id", UUID.class),
                rs.getObject("event_id", UUID.class),
                rs.getString("event_type"),
                rs.getObject("order_id", UUID.class),
                rs.getString("tracking_number"),
                rs.getInt("attempt_count"),
                httpStatus,
                errorClass,
                DlqFailureClassifier.describe(errorClass, httpStatus).summary(),
                instant(rs, "occurred_at"),
                instant(rs, "updated_at"),
                replayStatus == null ? null : WebhookDeliveryStatus.valueOf(replayStatus),
                instant(rs, "replayed_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
