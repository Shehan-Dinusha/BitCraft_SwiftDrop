package com.swiftdrop.delivery.statemachine.states;

import com.swiftdrop.contracts.enums.DeliveryState;
import com.swiftdrop.delivery.domain.entity.DeliverySession;
import com.swiftdrop.delivery.dto.TransitionDeliveryRequest;
import com.swiftdrop.delivery.statemachine.DeliveryStateHandler;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

@Component
public class CancelledState implements DeliveryStateHandler {

    @Override
    public DeliveryState state() {
        return DeliveryState.CANCELLED;
    }

    @Override
    public Set<DeliveryState> allowedNext() {
        return EnumSet.noneOf(DeliveryState.class);
    }

    @Override
    public void validate(TransitionDeliveryRequest request, DeliverySession session) {
        // Terminal state - no outgoing transitions allowed
    }
}

