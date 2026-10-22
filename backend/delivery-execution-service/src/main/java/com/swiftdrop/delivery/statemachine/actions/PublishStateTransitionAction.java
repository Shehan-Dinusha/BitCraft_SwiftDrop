package com.swiftdrop.delivery.statemachine.actions;

import com.swiftdrop.contracts.enums.DeliveryState;
import com.swiftdrop.contracts.events.delivery.DeliveryStateChangedEvent;
import com.swiftdrop.delivery.messaging.publisher.DeliveryEventPublisher;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PublishStateTransitionAction {
    private final DeliveryEventPublisher eventPublisher;

    public PublishStateTransitionAction(DeliveryEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void execute(UUID deliveryId, UUID orderId, UUID courierId,
                        DeliveryState previousState, DeliveryState newState, String notes) {
        eventPublisher.publishStateChanged(new DeliveryStateChangedEvent(
                null, null, deliveryId, orderId, courierId, previousState, newState, notes, null));
    }
}
