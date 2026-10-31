package com.swiftdrop.dispatch.messaging.publisher;

import com.swiftdrop.contracts.events.delivery.ParcelStatusChangedEvent;
import com.swiftdrop.contracts.events.dispatch.CourierAssignedEvent;
import com.swiftdrop.contracts.events.dispatch.DispatchFailedEvent;
import com.swiftdrop.dispatch.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class DispatchEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DispatchEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public DispatchEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishCourierAssigned(CourierAssignedEvent event) {
        log.info("Publishing CourierAssignedEvent for order: {}, courier: {}", event.orderId(), event.courierId());
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.ROUTING_KEY_COURIER_ASSIGNED,
                event
        );
    }

    public void publishDispatchFailed(DispatchFailedEvent event) {
        log.warn("Publishing DispatchFailedEvent for order: {}, reason: {}", event.orderId(), event.failureReason());
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.ROUTING_KEY_DISPATCH_FAILED,
                event
        );
    }

    public void publishParcelStatusChanged(ParcelStatusChangedEvent event) {
        log.info("Publishing ParcelStatusChangedEvent for order: {}, tracking: {}, status: {}",
                event.orderId(), event.trackingNumber(), event.newPublicStatus());
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.ROUTING_KEY_PARCEL_STATUS,
                event
        );
    }
}
