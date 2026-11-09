package com.swiftdrop.analytics.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.analytics.domain.repository.ProcessedTelemetryEventRepository;
import com.swiftdrop.analytics.messaging.TelemetryEvent;
import com.swiftdrop.contracts.events.delivery.ParcelStatusChangedEvent;
import com.swiftdrop.contracts.events.dispatch.CourierAssignedEvent;
import com.swiftdrop.contracts.events.order.OrderCreatedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TelemetryProcessingService {
    private final ProcessedTelemetryEventRepository events;
    private final SlaMetricsAggregationService metrics;
    private final ObjectMapper objectMapper;

    public TelemetryProcessingService(ProcessedTelemetryEventRepository events,
                                      SlaMetricsAggregationService metrics, ObjectMapper objectMapper) {
        this.events = events;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
    }

    /** JDBC inbox insert and existing JPA updates share the same datasource transaction. */
    @Transactional
    public void process(TelemetryEvent event) {
        String canonicalPayload;
        try {
            canonicalPayload = objectMapper.writeValueAsString(event.payload());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize canonical analytics event", ex);
        }
        if (!events.register(event, canonicalPayload)) return;

        switch (event.payload()) {
            case ParcelStatusChangedEvent status -> metrics.recordDeliveryEvent(status);
            case CourierAssignedEvent assignment -> metrics.recordCourierAssigned(assignment);
            case OrderCreatedEvent ignored -> {
                // Retain for Part 3; do not increment legacy parcel totals again.
            }
            default -> throw new IllegalArgumentException("Unsupported analytics event payload");
        }
    }
}
