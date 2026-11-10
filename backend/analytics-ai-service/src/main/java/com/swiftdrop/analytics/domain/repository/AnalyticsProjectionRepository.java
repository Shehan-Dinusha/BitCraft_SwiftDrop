package com.swiftdrop.analytics.domain.repository;

import com.swiftdrop.analytics.domain.projection.OrderProjection;
import com.swiftdrop.analytics.messaging.TelemetryEvent;
import com.swiftdrop.analytics.messaging.TelemetryEventDecoder;
import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.events.dispatch.CourierAssignedEvent;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Repository
public class AnalyticsProjectionRepository {
    private final JdbcTemplate jdbc;
    private final TelemetryEventDecoder decoder;

    public AnalyticsProjectionRepository(JdbcTemplate jdbc, TelemetryEventDecoder decoder) {
        this.jdbc = jdbc;
        this.decoder = decoder;
    }

    public void lockWrites() {
        // Database-wide transaction lock: protects live writers AND startup
        // backfill across replicas. Released automatically on commit/rollback.
        jdbc.execute("SELECT pg_advisory_xact_lock(3101003)");
    }

    public List<UUID> pendingOrders() {
        return jdbc.query("SELECT DISTINCT order_id FROM processed_telemetry_events WHERE NOT projection_applied ORDER BY order_id LIMIT 100",
                (rs, row) -> rs.getObject(1, UUID.class));
    }

    public List<TelemetryEvent> events(UUID orderId) {
        return jdbc.query("SELECT event_type, payload::text FROM processed_telemetry_events WHERE order_id = ?",
                (rs, row) -> {
                    var properties = new MessageProperties();
                    properties.setReceivedRoutingKey(rs.getString(1));
                    return decoder.decode(new Message(rs.getString(2).getBytes(StandardCharsets.UTF_8), properties)).orElseThrow();
                }, orderId);
    }

    public OrderProjection existing(UUID orderId) {
        return jdbc.query("SELECT * FROM analytics_order_projection WHERE order_id = ?", (rs, row) ->
                new OrderProjection(orderId, instant(rs, "created_at"), district(rs, "pickup_district"),
                        status(rs, "current_status"), instant(rs, "status_at"), district(rs, "district"),
                        rs.getObject("courier_id", UUID.class), instant(rs, "assigned_at"),
                        status(rs, "outcome"), instant(rs, "outcome_at"), district(rs, "outcome_district"),
                        rs.getObject("outcome_courier_id", UUID.class), rs.getString("conflict_reason")), orderId)
                .stream().findFirst().orElse(null);
    }

    public void replace(OrderProjection previous, OrderProjection next, List<TelemetryEvent> events) {
        var daily = new TreeMap<LocalDate, long[]>();
        var couriers = new HashMap<CourierDay, long[]>();
        contribute(previous, -1, daily, couriers);
        contribute(next, 1, daily, couriers);
        daily.forEach((date, delta) -> {
            if (Arrays.stream(delta).allMatch(n -> n == 0)) return;
            jdbc.update("INSERT INTO daily_delivery_stats(report_date) VALUES (?) ON CONFLICT DO NOTHING", date);
            jdbc.update("UPDATE daily_delivery_stats SET created=created+?, delivered=delivered+?, failed=failed+? WHERE report_date=?",
                    delta[0], delta[1], delta[2], date);
        });
        couriers.forEach((key, delta) -> {
            if (Arrays.stream(delta).allMatch(n -> n == 0)) return;
            jdbc.update("INSERT INTO courier_daily_stats(courier_id, report_date) VALUES (?, ?) ON CONFLICT DO NOTHING", key.courier(), key.date());
            jdbc.update("UPDATE courier_daily_stats SET delivered=delivered+?, failed=failed+?, active=active+? WHERE courier_id=? AND report_date=?",
                    delta[0], delta[1], delta[2], key.courier(), key.date());
        });
        jdbc.update("""
                INSERT INTO analytics_order_projection
                    (order_id, created_at, pickup_district, current_status, status_at, district, courier_id, assigned_at,
                     outcome, outcome_at, outcome_district, outcome_courier_id, conflict_reason)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (order_id) DO UPDATE SET created_at=EXCLUDED.created_at, pickup_district=EXCLUDED.pickup_district,
                    current_status=EXCLUDED.current_status, status_at=EXCLUDED.status_at, district=EXCLUDED.district,
                    courier_id=EXCLUDED.courier_id, assigned_at=EXCLUDED.assigned_at, outcome=EXCLUDED.outcome,
                    outcome_at=EXCLUDED.outcome_at, outcome_district=EXCLUDED.outcome_district,
                    outcome_courier_id=EXCLUDED.outcome_courier_id, conflict_reason=EXCLUDED.conflict_reason
                """, next.orderId(), timestamp(next.createdAt()), name(next.pickupDistrict()), name(next.currentStatus()),
                timestamp(next.statusAt()), name(next.district()), next.courierId(), timestamp(next.assignedAt()),
                name(next.outcome()), timestamp(next.outcomeAt()), name(next.outcomeDistrict()), next.outcomeCourierId(), next.conflictReason());
        for (var envelope : events) {
            if (envelope.payload() instanceof CourierAssignedEvent assignment) {
                jdbc.update("""
                        INSERT INTO analytics_couriers(courier_id, courier_name, last_assignment_at) VALUES (?, ?, ?)
                        ON CONFLICT (courier_id) DO UPDATE SET courier_name=EXCLUDED.courier_name,
                            last_assignment_at=EXCLUDED.last_assignment_at
                        WHERE EXCLUDED.last_assignment_at > analytics_couriers.last_assignment_at
                        """, assignment.courierId(), assignment.courierName(), timestamp(assignment.assignedAt()));
                jdbc.update("INSERT INTO analytics_order_couriers(order_id, courier_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                        next.orderId(), assignment.courierId());
            }
        }
        jdbc.update("UPDATE processed_telemetry_events SET projection_applied = TRUE WHERE order_id = ?", next.orderId());
    }

    private record CourierDay(UUID courier, LocalDate date) {}

    private static void contribute(OrderProjection state, int sign, Map<LocalDate, long[]> daily, Map<CourierDay, long[]> couriers) {
        if (state == null) return;
        if (state.createdAt() != null) daily.computeIfAbsent(OrderProjection.date(state.createdAt()), ignored -> new long[3])[0] += sign;
        if (state.completed()) {
            int index = state.outcome() == PublicOrderStatus.DELIVERED ? 1 : 2;
            LocalDate date = OrderProjection.date(state.outcomeAt());
            daily.computeIfAbsent(date, ignored -> new long[3])[index] += sign;
            if (state.outcomeCourierId() != null) {
                couriers.computeIfAbsent(new CourierDay(state.outcomeCourierId(), date), ignored -> new long[3])[index - 1] += sign;
            }
        }
        if (state.active()) {
            couriers.computeIfAbsent(new CourierDay(state.courierId(), OrderProjection.date(state.assignedAt())), ignored -> new long[3])[2] += sign;
        }
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
    private static SriLankanDistrict district(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value == null ? null : SriLankanDistrict.valueOf(value);
    }
    private static PublicOrderStatus status(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value == null ? null : PublicOrderStatus.valueOf(value);
    }
    private static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
    private static String name(Enum<?> value) { return value == null ? null : value.name(); }
}
