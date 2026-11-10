package com.swiftdrop.analytics.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.analytics.domain.repository.ProcessedTelemetryEventRepository;
import com.swiftdrop.analytics.domain.repository.AnalyticsProjectionRepository;
import com.swiftdrop.analytics.messaging.TelemetryEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TelemetryProcessingService {
    private final ProcessedTelemetryEventRepository events;
    private final AnalyticsProjectionService projections;
    private final AnalyticsProjectionRepository projectionRepository;
    private final ObjectMapper objectMapper;

    public TelemetryProcessingService(ProcessedTelemetryEventRepository events,
                                      AnalyticsProjectionService projections, AnalyticsProjectionRepository projectionRepository,
                                      ObjectMapper objectMapper) {
        this.events = events;
        this.projections = projections;
        this.projectionRepository = projectionRepository;
        this.objectMapper = objectMapper;
    }

    /** Inbox registration and all projection effects share one datasource transaction. */
    @Transactional
    public void process(TelemetryEvent event) {
        projectionRepository.lockWrites();
        String canonicalPayload;
        try {
            canonicalPayload = objectMapper.writeValueAsString(event.payload());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize canonical analytics event", ex);
        }
        if (!events.register(event, canonicalPayload)) return;

        projections.reconcile(event.orderId());
    }
}
