package com.swiftdrop.webhook.domain.repository;

import com.swiftdrop.webhook.delivery.ClaimedDelivery;
import com.swiftdrop.webhook.domain.entity.AttemptErrorClass;
import com.swiftdrop.webhook.domain.entity.WebhookDeliveryStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * PostgreSQL statements for delivery jobs (Step 9). Every method runs on the caller's transaction
 * ({@code DeliveryAttemptRecorder}); none of them makes an HTTP call. Times that decide eligibility
 * ({@code next_attempt_at}, {@code lease_expires_at}) are computed with the database clock ({@code now()}), the same
 * clock the claim query compares against.
 *
 * <p>Status values and transitions respect the V3 constraints: {@code ck_webhook_deliveries_status},
 * {@code ck_webhook_deliveries_schedule} (PENDING/RETRY need {@code next_attempt_at}), {@code ck_webhook_deliveries_lease}
 * (a lease exists exactly while IN_FLIGHT), and on attempts {@code ck_webhook_delivery_attempts_model} (a job attempt
 * has {@code delivery_id} and no payload copy) plus the unique index on {@code (delivery_id, attempt_number)}.</p>
 */
@Repository
public class DeliveryJobRepository {

    private static final RowMapper<ClaimedDelivery> CLAIMED = (rs, i) -> new ClaimedDelivery(
            rs.getObject("delivery_id", UUID.class),
            rs.getObject("inbound_event_id", UUID.class),
            rs.getObject("endpoint_id", UUID.class),
            rs.getObject("merchant_id", UUID.class),
            rs.getString("event_type"),
            rs.getInt("attempt_count"),
            rs.getString("lease_owner"),
            rs.getTimestamp("lease_expires_at").toInstant());

    private final NamedParameterJdbcTemplate jdbc;

    public DeliveryJobRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Leases up to {@code limit} due jobs to {@code owner}: PENDING/RETRY → IN_FLIGHT, {@code attempt_count + 1}.
     *
     * <ul>
     *   <li><b>Due:</b> {@code next_attempt_at <= now()} and {@code attempt_count < maxAttempts} (a job can never be
     *       claimed for a fifth attempt, whatever its status says).</li>
     *   <li><b>Paused endpoints (D8):</b> jobs of an inactive endpoint are not claimed; they stay PENDING/RETRY and
     *       become eligible again as soon as the endpoint is re-enabled.</li>
     *   <li><b>Ordering (D7):</b> not claimed while an older active job (PENDING/RETRY/IN_FLIGHT) exists for the same
     *       endpoint and order. Age = the event's {@code occurred_at}, then the job's {@code created_at}, then its id.
     *       SUCCEEDED and DEAD jobs do not block. The check reads other rows without locking them, so a job locked by
     *       another claimer still blocks its successors.</li>
     *   <li><b>Concurrency:</b> {@code FOR UPDATE OF d SKIP LOCKED}: concurrent claimers on any replica get disjoint
     *       jobs, and PostgreSQL re-checks the conditions on the locked row's latest version.</li>
     * </ul>
     * The eligibility check needs no extra columns: the order and occurrence time come from {@code inbound_events}
     * (indexed by {@code idx_inbound_events_order}).
     */
    public List<ClaimedDelivery> claimDue(String owner, Duration lease, int limit, int maxAttempts) {
        var params = new MapSqlParameterSource()
                .addValue("owner", owner, Types.VARCHAR)
                .addValue("leaseMs", lease.toMillis(), Types.BIGINT)
                .addValue("limit", limit, Types.INTEGER)
                .addValue("maxAttempts", maxAttempts, Types.INTEGER);
        return jdbc.query("""
                WITH due AS (
                    SELECT d.delivery_id, ev.event_type
                    FROM webhook_deliveries d
                    JOIN webhook_endpoints e ON e.endpoint_id = d.endpoint_id AND e.merchant_id = d.merchant_id
                    JOIN inbound_events ev ON ev.event_id = d.inbound_event_id
                    WHERE d.status IN ('PENDING', 'RETRY')
                      AND d.next_attempt_at <= now()
                      AND d.attempt_count < :maxAttempts
                      AND e.is_active
                      AND NOT EXISTS (
                          SELECT 1
                          FROM webhook_deliveries o
                          JOIN inbound_events oev ON oev.event_id = o.inbound_event_id
                          WHERE o.endpoint_id = d.endpoint_id
                            AND oev.order_id = ev.order_id
                            AND o.status IN ('PENDING', 'RETRY', 'IN_FLIGHT')
                            AND (oev.occurred_at, o.created_at, o.delivery_id)
                                < (ev.occurred_at, d.created_at, d.delivery_id))
                    ORDER BY d.next_attempt_at, d.created_at, d.delivery_id
                    LIMIT :limit
                    FOR UPDATE OF d SKIP LOCKED
                )
                UPDATE webhook_deliveries w
                SET status = 'IN_FLIGHT',
                    attempt_count = w.attempt_count + 1,
                    lease_owner = :owner,
                    lease_expires_at = now() + (:leaseMs * INTERVAL '1 millisecond'),
                    version = w.version + 1,
                    updated_at = now()
                FROM due
                WHERE w.delivery_id = due.delivery_id
                RETURNING w.delivery_id, w.inbound_event_id, w.endpoint_id, w.merchant_id, due.event_type,
                          w.attempt_count, w.lease_owner, w.lease_expires_at
                """, params, CLAIMED);
    }

    /**
     * Fenced transition for the result of one attempt. Applies only if the job is still {@code IN_FLIGHT}, leased to
     * {@code owner}, for {@code attemptNumber}; otherwise (stale lease, recovered, re-claimed, already terminal) nothing
     * changes and the result is empty. The row lock taken here is held until the caller's transaction ends.
     *
     * @param retryDelay required for RETRY (sets {@code next_attempt_at = now() + delay}); null for SUCCEEDED/DEAD,
     *                   which clear {@code next_attempt_at}
     * @return the applied transition, or empty if the fence did not match (stale result: nothing was changed)
     */
    public Optional<AppliedTransition> completeAttempt(UUID deliveryId, String owner, int attemptNumber,
                                                       WebhookDeliveryStatus newStatus, Duration retryDelay) {
        if (newStatus != WebhookDeliveryStatus.SUCCEEDED && newStatus != WebhookDeliveryStatus.RETRY
                && newStatus != WebhookDeliveryStatus.DEAD) {
            throw new IllegalArgumentException("An attempt can only end in SUCCEEDED, RETRY or DEAD");
        }
        if ((newStatus == WebhookDeliveryStatus.RETRY) != (retryDelay != null)) {
            throw new IllegalArgumentException("A retry delay is required for RETRY and only for RETRY");
        }
        var params = new MapSqlParameterSource()
                .addValue("deliveryId", deliveryId, Types.OTHER)
                .addValue("owner", owner, Types.VARCHAR)
                .addValue("attempt", attemptNumber, Types.INTEGER)
                .addValue("status", newStatus.name(), Types.VARCHAR)
                .addValue("delayMs", retryDelay == null ? null : retryDelay.toMillis(), Types.BIGINT);
        List<AppliedTransition> updated = jdbc.query("""
                UPDATE webhook_deliveries
                SET status = :status,
                    next_attempt_at = now() + (CAST(:delayMs AS BIGINT) * INTERVAL '1 millisecond'),
                    lease_owner = NULL,
                    lease_expires_at = NULL,
                    version = version + 1,
                    updated_at = now()
                WHERE delivery_id = :deliveryId
                  AND status = 'IN_FLIGHT'
                  AND lease_owner = :owner
                  AND attempt_count = :attempt
                RETURNING next_attempt_at
                """, params, (rs, i) -> {
            Timestamp next = rs.getTimestamp("next_attempt_at");
            return new AppliedTransition(next == null ? null : next.toInstant());
        });
        return updated.isEmpty() ? Optional.empty() : Optional.of(updated.getFirst());
    }

    /** @param nextAttemptAt the stored {@code next_attempt_at}: set for RETRY, null for SUCCEEDED and DEAD */
    public record AppliedTransition(Instant nextAttemptAt) {
    }

    /**
     * Undoes a claim when <b>no HTTP request was made</b> (endpoint paused after the claim, or no local per-endpoint
     * capacity): back to PENDING (if this would have been attempt 1) or RETRY, {@code attempt_count - 1}, lease
     * cleared, {@code next_attempt_at} unchanged (still non-null from before the claim). Same fence as
     * {@link #completeAttempt}, so a recovered or re-claimed job is never touched. No attempt row is written.
     *
     * @return true if the claim was released
     */
    public boolean releaseClaim(UUID deliveryId, String owner, int attemptNumber) {
        var params = new MapSqlParameterSource()
                .addValue("deliveryId", deliveryId, Types.OTHER)
                .addValue("owner", owner, Types.VARCHAR)
                .addValue("attempt", attemptNumber, Types.INTEGER);
        return jdbc.update("""
                UPDATE webhook_deliveries
                SET status = CASE WHEN attempt_count = 1 THEN 'PENDING' ELSE 'RETRY' END,
                    attempt_count = attempt_count - 1,
                    lease_owner = NULL,
                    lease_expires_at = NULL,
                    version = version + 1,
                    updated_at = now()
                WHERE delivery_id = :deliveryId
                  AND status = 'IN_FLIGHT'
                  AND lease_owner = :owner
                  AND attempt_count = :attempt
                """, params) == 1;
    }

    /**
     * One {@code webhook_delivery_attempts} row for a job attempt (no payload copy, no response body). The unique
     * index on {@code (delivery_id, attempt_number)} makes a second row for the same attempt fail, which rolls back
     * the caller's transaction including the job transition.
     */
    public void insertAttempt(UUID attemptId, ClaimedDelivery job, Integer httpStatus, boolean success,
                              AttemptErrorClass errorClass, String errorMessage, Integer latencyMs,
                              Instant attemptedAt) {
        var params = new MapSqlParameterSource()
                .addValue("attemptId", attemptId, Types.OTHER)
                .addValue("deliveryId", job.deliveryId(), Types.OTHER)
                .addValue("endpointId", job.endpointId(), Types.OTHER)
                .addValue("merchantId", job.merchantId(), Types.OTHER)
                .addValue("eventType", job.eventType(), Types.VARCHAR)
                .addValue("httpStatus", httpStatus, Types.INTEGER)
                .addValue("attemptNumber", job.attemptNumber(), Types.INTEGER)
                .addValue("success", success, Types.BOOLEAN)
                .addValue("errorClass", errorClass == null ? null : errorClass.name(), Types.VARCHAR)
                .addValue("errorMessage", errorMessage, Types.VARCHAR)
                .addValue("latencyMs", latencyMs, Types.INTEGER)
                .addValue("attemptedAt", OffsetDateTime.ofInstant(attemptedAt, ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE);
        jdbc.update("""
                INSERT INTO webhook_delivery_attempts (attempt_id, delivery_id, endpoint_id, merchant_id, event_type,
                                                       payload, http_status_code, attempt_number, is_success,
                                                       response_body, error_message, error_class, latency_ms,
                                                       attempted_at)
                VALUES (:attemptId, :deliveryId, :endpointId, :merchantId, :eventType,
                        NULL, :httpStatus, :attemptNumber, :success,
                        NULL, :errorMessage, :errorClass, :latencyMs,
                        :attemptedAt)
                """, params);
    }

    /**
     * Locks up to {@code limit} jobs whose lease has expired (the worker crashed or stalled), skipping rows another
     * recovery run already holds. Returned in the same shape as a claim, so the caller can close the lost attempt
     * through the normal fenced path.
     */
    public List<ClaimedDelivery> lockExpiredLeases(int limit) {
        return jdbc.query("""
                SELECT d.delivery_id, d.inbound_event_id, d.endpoint_id, d.merchant_id, ev.event_type,
                       d.attempt_count, d.lease_owner, d.lease_expires_at
                FROM webhook_deliveries d
                JOIN inbound_events ev ON ev.event_id = d.inbound_event_id
                WHERE d.status = 'IN_FLIGHT'
                  AND d.lease_expires_at < now()
                ORDER BY d.lease_expires_at
                LIMIT :limit
                FOR UPDATE OF d SKIP LOCKED
                """, new MapSqlParameterSource("limit", limit), CLAIMED);
    }
}
