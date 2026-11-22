package com.swiftdrop.delivery.statemachine.states;

import com.swiftdrop.contracts.enums.DeliveryState;
import com.swiftdrop.delivery.statemachine.DeliveryStateHandler;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/** A courier is bringing it back to the sender; back to the hub if the sender cannot take it. */
@Component
public class OutForReturnState implements DeliveryStateHandler {

    @Override
    public DeliveryState state() {
        return DeliveryState.OUT_FOR_RETURN;
    }

    @Override
    public Set<DeliveryState> allowedNext() {
        return EnumSet.of(DeliveryState.RETURNED, DeliveryState.RETURN_AT_HUB, DeliveryState.FAILED);
    }
}
