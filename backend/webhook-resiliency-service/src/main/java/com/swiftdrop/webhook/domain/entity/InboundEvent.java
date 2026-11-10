package com.swiftdrop.webhook.domain.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * An event received from RabbitMQ (transactional inbox row).
 *
 * <p>Rows are inserted and resolved only through
 * {@link com.swiftdrop.webhook.domain.repository.IngestionJdbcRepository}, which uses
 * {@code INSERT ... ON CONFLICT} so that the primary key on the producer's event id, not an
 * application-level check, decides whether an event is new. The entity is therefore read-only.</p>
 */
@Entity
@Table(name = "inbound_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InboundEvent {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 100, updatable = false)
    private String eventType;

    @Column(name = "schema_version", nullable = false, length = 16, updatable = false)
    private String schemaVersion;

    @Column(name = "source_service", length = 100, updatable = false)
    private String sourceService;

    @Column(name = "correlation_id", nullable = false, updatable = false)
    private UUID correlationId;

    @Column(name = "order_id", updatable = false)
    private UUID orderId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "jsonb")
    private JsonNode payload;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 32)
    private InboundEventStatus processingStatus;

    @Column(name = "merchant_id")
    private UUID merchantId;

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
