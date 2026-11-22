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
public class InTransitState implements DeliveryStateHandler {

    @Override
    public DeliveryState state() {
        return DeliveryState.IN_TRANSIT;
    }

    @Override
    public Set<DeliveryState> allowedNext() {
        return EnumSet.of(DeliveryState.OUT_FOR_DELIVERY, DeliveryState.AT_DESTINATION_HUB, DeliveryState.FAILED);
    }

    @Override
    public void validate(TransitionDeliveryRequest request, DeliverySession session) {
        // Same district goes straight on; different districts go through the hubs (see HubRoute).
        HubRoute.requireRoute(DeliveryState.IN_TRANSIT, request.targetState(), session);
    }
}

