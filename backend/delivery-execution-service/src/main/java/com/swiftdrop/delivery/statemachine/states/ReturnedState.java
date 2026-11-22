package com.swiftdrop.delivery.statemachine.states;

import com.swiftdrop.contracts.enums.DeliveryState;
import com.swiftdrop.delivery.statemachine.DeliveryStateHandler;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/** The sender has it back. */
@Component
public class ReturnedState implements DeliveryStateHandler {

    @Override
    public DeliveryState state() {
        return DeliveryState.RETURNED;
    }

    @Override
    public Set<DeliveryState> allowedNext() {
        return EnumSet.noneOf(DeliveryState.class);
    }
}
