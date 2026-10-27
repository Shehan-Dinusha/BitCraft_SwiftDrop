package com.swiftdrop.analytics.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.analytics.config.RabbitMQConfig;
import com.swiftdrop.analytics.service.SlaMetricsAggregationService;
import com.swiftdrop.contracts.events.delivery.ParcelStatusChangedEvent;
import com.swiftdrop.contracts.events.dispatch.CourierAssignedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class TelemetryAggregationConsumer {

    private static final Logger log = LoggerFactory.getLogger(TelemetryAggregationConsumer.class);

    private final SlaMetricsAggregationService metricsService;
    private final ObjectMapper objectMapper;

    public TelemetryAggregationConsumer(SlaMetricsAggregationService metricsService, ObjectMapper objectMapper) {
        this.metricsService = metricsService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RabbitMQConfig.ANALYTICS_EVENTS_QUEUE)
    public void processMessage(Object payload) {
        try {
            log.debug("Analytics telemetry consumer received payload: {}", payload);
            if (payload instanceof ParcelStatusChangedEvent event) {
                metricsService.recordDeliveryEvent(event);
            } else if (payload instanceof CourierAssignedEvent event) {
                metricsService.recordCourierAssigned(event);
            } else if (payload instanceof Map<?, ?> map) {
                if (map.containsKey("newPublicStatus") || map.containsKey("internalSubStatus")) {
                    ParcelStatusChangedEvent event = objectMapper.convertValue(map, ParcelStatusChangedEvent.class);
                    metricsService.recordDeliveryEvent(event);
                } else if (map.containsKey("courierId") && map.containsKey("courierName")) {
                    CourierAssignedEvent event = objectMapper.convertValue(map, CourierAssignedEvent.class);
                    metricsService.recordCourierAssigned(event);
                }
            }
        } catch (Exception ex) {
            log.error("Failed to process telemetry event in analytics service: {}", ex.getMessage(), ex);
            throw new RuntimeException("Analytics telemetry processing failed", ex);
        }
    }
}
