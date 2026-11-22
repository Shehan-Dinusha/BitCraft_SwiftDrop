package com.swiftdrop.delivery.statemachine.states;

import com.swiftdrop.contracts.enums.DeliveryState;
import com.swiftdrop.delivery.domain.entity.DeliverySession;
import com.swiftdrop.delivery.dto.TransitionDeliveryRequest;
import com.swiftdrop.delivery.statemachine.DeliveryStateHandler;
import com.swiftdrop.delivery.statemachine.ReturnRoute;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/** At the hub after a failed attempt: tried again, or returned to the sender. */
@Component
public class HeldAtHubState implements DeliveryStateHandler {

    @Override
    public DeliveryState state() {
        return DeliveryState.HELD_AT_HUB;
    }

    @Override
    public Set<DeliveryState> allowedNext() {
        return EnumSet.of(DeliveryState.AT_DESTINATION_HUB, DeliveryState.RETURN_IN_TRANSIT, DeliveryState.RETURN_AT_HUB, DeliveryState.FAILED);
    }

    @Override
    public void validate(TransitionDeliveryRequest request, DeliverySession session) {
        ReturnRoute.requireRoute(DeliveryState.HELD_AT_HUB, request.targetState(), session);
    }
}
