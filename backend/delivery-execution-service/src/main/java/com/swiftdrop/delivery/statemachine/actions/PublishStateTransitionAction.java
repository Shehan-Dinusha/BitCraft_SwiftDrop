package com.swiftdrop.delivery.statemachine.actions;

import com.swiftdrop.contracts.enums.DeliveryFailureReason;
import com.swiftdrop.contracts.enums.DeliveryState;
import com.swiftdrop.contracts.enums.InternalQueueStatus;
import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.events.delivery.DeliveryStateChangedEvent;
import com.swiftdrop.contracts.events.delivery.ParcelStatusChangedEvent;
import com.swiftdrop.delivery.messaging.publisher.DeliveryEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class PublishStateTransitionAction {

    private final DeliveryEventPublisher eventPublisher;

    public PublishStateTransitionAction(DeliveryEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void execute(UUID deliveryId, UUID orderId, String trackingNumber, UUID courierId,
                        DeliveryState previousState, DeliveryState newState, String notes,
                        SriLankanDistrict pickupDistrict, SriLankanDistrict deliveryDistrict,
                        DeliveryFailureReason failureReason) {

        // 1. Publish internal delivery state changed event
        eventPublisher.publishStateChanged(new DeliveryStateChangedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                deliveryId,
                orderId,
                courierId,
                previousState,
                newState,
                notes,
                Instant.now()
        ));

        // 2. Map and publish public tracking event for Gateway / Customer streams
        PublicOrderStatus publicStatus = PublicOrderStatus.fromDeliveryState(newState);
        PublicOrderStatus prevPublicStatus = PublicOrderStatus.fromDeliveryState(previousState);
        InternalQueueStatus internalSubStatus = InternalQueueStatus.fromDeliveryState(newState);

        eventPublisher.publishParcelStatusChanged(new ParcelStatusChangedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                trackingNumber,
                orderId,
                null,
                prevPublicStatus,
                publicStatus,
                internalSubStatus,
                pickupDistrict,
                deliveryDistrict,
                failureReason,
                Instant.now()
        ));
    }
}
