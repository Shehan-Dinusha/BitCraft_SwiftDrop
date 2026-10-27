package com.swiftdrop.dispatch.messaging.consumer;

import com.swiftdrop.contracts.events.delivery.ParcelStatusChangedEvent;
import com.swiftdrop.dispatch.config.RabbitMQConfig;
import com.swiftdrop.dispatch.service.DispatchAssignmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ParcelStatusSyncConsumer {

    private static final Logger log = LoggerFactory.getLogger(ParcelStatusSyncConsumer.class);

    private final DispatchAssignmentService dispatchAssignmentService;

    public ParcelStatusSyncConsumer(DispatchAssignmentService dispatchAssignmentService) {
        this.dispatchAssignmentService = dispatchAssignmentService;
    }

    @RabbitListener(queues = RabbitMQConfig.PARCEL_STATUS_QUEUE)
    public void onParcelStatusChanged(ParcelStatusChangedEvent event) {
        log.debug("Received ParcelStatusChangedEvent for order: {}, tracking: {}, status: {}",
                event.orderId(), event.trackingNumber(), event.newPublicStatus());
        try {
            dispatchAssignmentService.handleParcelStatusChanged(event);
        } catch (Exception ex) {
            log.error("Failed to sync parcel status in dispatch service: {}", event.trackingNumber(), ex);
        }
    }
}
