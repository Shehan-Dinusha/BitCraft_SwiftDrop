package com.swiftdrop.delivery.statemachine.states;

import com.swiftdrop.contracts.enums.DeliveryState;
import com.swiftdrop.delivery.domain.entity.DeliverySession;
import com.swiftdrop.delivery.dto.TransitionDeliveryRequest;
import com.swiftdrop.delivery.statemachine.DeliveryStateHandler;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

@Component
public class AssignedState implements DeliveryStateHandler {

    @Override
    public DeliveryState state() {
        return DeliveryState.ASSIGNED;
    }

    @Override
    public Set<DeliveryState> allowedNext() {
        return EnumSet.of(DeliveryState.PICKED_UP, DeliveryState.FAILED, DeliveryState.CANCELLED);
    }

    @Override
    public void validate(TransitionDeliveryRequest request, DeliverySession session) {
        // Preconditions evaluated when moving from ASSIGNED
    }
}

