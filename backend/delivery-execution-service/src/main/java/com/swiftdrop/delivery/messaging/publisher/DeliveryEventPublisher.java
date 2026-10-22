package com.swiftdrop.delivery.messaging.publisher;

import com.swiftdrop.contracts.events.delivery.DeliveryStateChangedEvent;
import com.swiftdrop.contracts.events.delivery.LocationUpdatedEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class DeliveryEventPublisher {
    public static final String EVENTS_EXCHANGE = "swiftdrop.events.exchange";
    public static final String PARCEL_STATUS_CHANGED_ROUTING_KEY = "parcel.status.changed";

    private final RabbitTemplate rabbitTemplate;

    public DeliveryEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishStateChanged(DeliveryStateChangedEvent event) {
        rabbitTemplate.convertAndSend(EVENTS_EXCHANGE, PARCEL_STATUS_CHANGED_ROUTING_KEY, event);
    }

    public void publishLocationUpdated(LocationUpdatedEvent event) {
        rabbitTemplate.convertAndSend(EVENTS_EXCHANGE, PARCEL_STATUS_CHANGED_ROUTING_KEY, event);
    }
}
