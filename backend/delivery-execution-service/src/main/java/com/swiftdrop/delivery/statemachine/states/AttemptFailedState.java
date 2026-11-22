package com.swiftdrop.delivery.statemachine.states;

import com.swiftdrop.contracts.enums.DeliveryState;
import com.swiftdrop.delivery.statemachine.DeliveryStateHandler;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/** The courier has a parcel whose delivery failed and brings it to the hub. */
@Component
public class AttemptFailedState implements DeliveryStateHandler {

    @Override
    public DeliveryState state() {
        return DeliveryState.ATTEMPT_FAILED;
    }

    @Override
    public Set<DeliveryState> allowedNext() {
        return EnumSet.of(DeliveryState.HELD_AT_HUB, DeliveryState.FAILED);
    }
}
