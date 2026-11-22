package com.swiftdrop.delivery.statemachine.states;

import com.swiftdrop.contracts.enums.DeliveryState;
import com.swiftdrop.delivery.statemachine.DeliveryStateHandler;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/** On its way back to the sender's hub. */
@Component
public class ReturnInTransitState implements DeliveryStateHandler {

    @Override
    public DeliveryState state() {
        return DeliveryState.RETURN_IN_TRANSIT;
    }

    @Override
    public Set<DeliveryState> allowedNext() {
        return EnumSet.of(DeliveryState.RETURN_AT_HUB, DeliveryState.FAILED);
    }
}
