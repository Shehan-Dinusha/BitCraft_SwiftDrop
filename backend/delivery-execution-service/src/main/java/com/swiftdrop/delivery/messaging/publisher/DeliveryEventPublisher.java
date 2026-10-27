package com.swiftdrop.delivery.messaging.publisher;

import com.swiftdrop.contracts.events.delivery.DeliveryStateChangedEvent;
import com.swiftdrop.contracts.events.delivery.LocationUpdatedEvent;
import com.swiftdrop.contracts.events.delivery.ParcelStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class DeliveryEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DeliveryEventPublisher.class);

    public static final String EVENTS_EXCHANGE = "swiftdrop.events.exchange";

    public static final String ROUTING_KEY_DELIVERY_STATE_CHANGED = "delivery.state.changed";
    public static final String ROUTING_KEY_LOCATION_UPDATED = "delivery.location.updated";
    public static final String ROUTING_KEY_PARCEL_STATUS_CHANGED = "parcel.status.changed";

    private final RabbitTemplate rabbitTemplate;

    public DeliveryEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishStateChanged(DeliveryStateChangedEvent event) {
        log.info("Publishing DeliveryStateChangedEvent for delivery: {}, state: {} -> {}",
                event.deliveryId(), event.previousState(), event.newState());
        rabbitTemplate.convertAndSend(
                EVENTS_EXCHANGE,
                ROUTING_KEY_DELIVERY_STATE_CHANGED,
                event
        );
    }

    public void publishLocationUpdated(LocationUpdatedEvent event) {
        log.debug("Publishing LocationUpdatedEvent for delivery: {}, courier: {}",
                event.deliveryId(), event.courierId());
        rabbitTemplate.convertAndSend(
                EVENTS_EXCHANGE,
                ROUTING_KEY_LOCATION_UPDATED,
                event
        );
    }

    public void publishParcelStatusChanged(ParcelStatusChangedEvent event) {
        log.info("Publishing ParcelStatusChangedEvent for order: {}, tracking: {}, publicStatus: {}",
                event.orderId(), event.trackingNumber(), event.newPublicStatus());
        rabbitTemplate.convertAndSend(
                EVENTS_EXCHANGE,
                ROUTING_KEY_PARCEL_STATUS_CHANGED,
                event
        );
    }
}
