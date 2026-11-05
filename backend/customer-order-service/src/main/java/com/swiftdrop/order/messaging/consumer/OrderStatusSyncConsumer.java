package com.swiftdrop.order.messaging.consumer;

import com.swiftdrop.contracts.events.delivery.ParcelStatusChangedEvent;
import com.swiftdrop.order.config.RabbitMQConfig;
import com.swiftdrop.order.service.OrderStatusSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Applies {@code parcel.status.changed} events to the matching order. Skips are handled inside the service
 * and never thrown. A genuine failure propagates, so the listener's retry and then dead-letter
 * configuration (see {@code RabbitMQConfig#statusSyncListenerContainerFactory}) takes over instead of the message looping forever.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderStatusSyncConsumer {

    private final OrderStatusSyncService orderStatusSyncService;

    @RabbitListener(queues = RabbitMQConfig.STATUS_SYNC_QUEUE,
            containerFactory = RabbitMQConfig.STATUS_SYNC_LISTENER_FACTORY)
    public void onParcelStatusChanged(ParcelStatusChangedEvent event) {
        OrderStatusSyncService.Outcome outcome = orderStatusSyncService.apply(event);
        log.debug("Handled parcel status event eventId={}, outcome={}", event.eventId(), outcome);
    }
}
