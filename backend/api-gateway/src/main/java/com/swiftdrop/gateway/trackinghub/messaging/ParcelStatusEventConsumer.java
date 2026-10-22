package com.swiftdrop.gateway.trackinghub.messaging;

import com.swiftdrop.contracts.events.delivery.DeliveryStateChangedEvent;
import com.swiftdrop.contracts.events.delivery.LocationUpdatedEvent;
import com.swiftdrop.gateway.trackinghub.service.TrackingBroadcastHubService;
import com.swiftdrop.gateway.trackinghub.service.TrackingDataSanitizerProxy;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ParcelStatusEventConsumer {
    private final TrackingBroadcastHubService broadcastHub;
    private final TrackingDataSanitizerProxy sanitizer;

    public ParcelStatusEventConsumer(TrackingBroadcastHubService broadcastHub,
                                     TrackingDataSanitizerProxy sanitizer) {
        this.broadcastHub = broadcastHub;
        this.sanitizer = sanitizer;
    }

    @RabbitListener(queues = "swiftdrop.gateway.tracking.queue")
    public void onStateChanged(DeliveryStateChangedEvent event) {
        broadcastHub.publish(sanitizer.sanitize(event));
    }

    @RabbitListener(queues = "swiftdrop.gateway.tracking.queue")
    public void onLocationUpdated(LocationUpdatedEvent event) {
        broadcastHub.publish(sanitizer.sanitize(event));
    }
}
