package com.swiftdrop.delivery.service;

import com.swiftdrop.contracts.events.delivery.LocationUpdatedEvent;
import com.swiftdrop.delivery.messaging.publisher.DeliveryEventPublisher;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class DeliveryExecutionService {
    private final DeliveryEventPublisher eventPublisher;

    public DeliveryExecutionService(DeliveryEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public String status() { return "ready"; }

    public void publishCourierLocation(UUID deliveryId, UUID courierId, double latitude,
                                       double longitude, double bearingDegrees,
                                       double speedKmh, double accuracy) {
        eventPublisher.publishLocationUpdated(new LocationUpdatedEvent(
                null, null, deliveryId, courierId, latitude, longitude,
                bearingDegrees, speedKmh, accuracy, null));
    }
}
