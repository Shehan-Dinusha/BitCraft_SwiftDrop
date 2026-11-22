package com.swiftdrop.delivery.statemachine.states;

import com.swiftdrop.contracts.enums.DeliveryState;
import com.swiftdrop.delivery.domain.entity.DeliverySession;
import com.swiftdrop.delivery.dto.TransitionDeliveryRequest;
import com.swiftdrop.delivery.exception.InvalidStateTransitionException;
import com.swiftdrop.delivery.statemachine.DeliveryStateHandler;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/** At the sender's hub, waiting for a courier to bring it back. */
@Component
public class ReturnAtHubState implements DeliveryStateHandler {

    @Override
    public DeliveryState state() {
        return DeliveryState.RETURN_AT_HUB;
    }

    @Override
    public Set<DeliveryState> allowedNext() {
        return EnumSet.of(DeliveryState.OUT_FOR_RETURN, DeliveryState.FAILED);
    }

    @Override
    public void validate(TransitionDeliveryRequest request, DeliverySession session) {
        if (request.targetState() == DeliveryState.OUT_FOR_RETURN && session.getCourierId() == null) {
            throw new InvalidStateTransitionException("Assign a courier of this district before the parcel leaves the hub");
        }
    }
}
