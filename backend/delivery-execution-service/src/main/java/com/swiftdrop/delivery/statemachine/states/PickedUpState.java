package com.swiftdrop.delivery.statemachine.states;

import com.swiftdrop.contracts.enums.DeliveryState;
import com.swiftdrop.delivery.domain.entity.DeliverySession;
import com.swiftdrop.delivery.dto.TransitionDeliveryRequest;
import com.swiftdrop.delivery.statemachine.DeliveryStateHandler;
import com.swiftdrop.delivery.statemachine.HubRoute;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

@Component
public class PickedUpState implements DeliveryStateHandler {

    @Override
    public DeliveryState state() {
        return DeliveryState.PICKED_UP;
    }

    @Override
    public Set<DeliveryState> allowedNext() {
        // AT_DESTINATION_HUB: a Same day parcel the courier could not deliver today, brought back to the hub.
        return EnumSet.of(DeliveryState.IN_TRANSIT, DeliveryState.AT_ORIGIN_HUB, DeliveryState.AT_DESTINATION_HUB,
                DeliveryState.ATTEMPT_FAILED, DeliveryState.FAILED);
    }

    @Override
    public void validate(TransitionDeliveryRequest request, DeliverySession session) {
        // Same district goes straight on; different districts go through the hubs (see HubRoute).
        HubRoute.requireRoute(DeliveryState.PICKED_UP, request.targetState(), session);
    }
}

