package com.swiftdrop.analytics.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.analytics.service.SlaMetricsAggregationService;
import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.events.delivery.ParcelStatusChangedEvent;
import com.swiftdrop.contracts.events.dispatch.CourierAssignedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TelemetryAggregationConsumerTest {

    @Mock
    private SlaMetricsAggregationService metricsService;

    private ObjectMapper objectMapper;
    private TelemetryAggregationConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        consumer = new TelemetryAggregationConsumer(metricsService, objectMapper);
    }

    @Test
    @DisplayName("processMessage routes ParcelStatusChangedEvent to metricsService")
    void processMessage_parcelStatusChangedEvent() {
        ParcelStatusChangedEvent event = new ParcelStatusChangedEvent(
                UUID.randomUUID(), UUID.randomUUID(), "SWD-123", UUID.randomUUID(), UUID.randomUUID(),
                PublicOrderStatus.IN_TRANSIT, PublicOrderStatus.DELIVERED, null,
                SriLankanDistrict.COLOMBO, SriLankanDistrict.COLOMBO, null, Instant.now()
        );

        consumer.processMessage(event);

        verify(metricsService).recordDeliveryEvent(event);
    }

    @Test
    @DisplayName("processMessage routes CourierAssignedEvent to metricsService")
    void processMessage_courierAssignedEvent() {
        CourierAssignedEvent event = new CourierAssignedEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Kamal Gunaratne", 20, Instant.now(), Instant.now()
        );

        consumer.processMessage(event);

        verify(metricsService).recordCourierAssigned(event);
    }

    @Test
    @DisplayName("processMessage converts Map payload to ParcelStatusChangedEvent")
    void processMessage_mapPayloadHandling() {
        Map<String, Object> map = Map.of(
                "trackingNumber", "SWD-999",
                "newPublicStatus", "DELIVERED",
                "district", "COLOMBO"
        );

        consumer.processMessage(map);

        verify(metricsService).recordDeliveryEvent(any(ParcelStatusChangedEvent.class));
    }
}
