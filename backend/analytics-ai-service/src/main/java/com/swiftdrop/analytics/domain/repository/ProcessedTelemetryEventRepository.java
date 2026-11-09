package com.swiftdrop.analytics.domain.repository;

import com.swiftdrop.analytics.messaging.TelemetryEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;

@Repository
public class ProcessedTelemetryEventRepository {
    private final JdbcTemplate jdbc;

    public ProcessedTelemetryEventRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean register(TelemetryEvent event, String canonicalPayload) {
        return jdbc.update("""
                INSERT INTO processed_telemetry_events
                    (event_id, event_type, order_id, correlation_id, occurred_at, payload)
                VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb))
                ON CONFLICT (event_id) DO NOTHING
                """, event.eventId(), event.eventType(), event.orderId(), event.correlationId(),
                Timestamp.from(event.occurredAt()), canonicalPayload) == 1;
    }
}
