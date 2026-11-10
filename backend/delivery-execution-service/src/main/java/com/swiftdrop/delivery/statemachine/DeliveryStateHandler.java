package com.swiftdrop.delivery.statemachine;

import com.swiftdrop.contracts.enums.DeliveryState;
import com.swiftdrop.delivery.domain.entity.DeliverySession;
import com.swiftdrop.delivery.dto.TransitionDeliveryRequest;

import java.util.Set;

/**
 * State handler contract for the Delivery finite state machine (State Pattern).
 * Each concrete {@link DeliveryState} implements this interface to declare its
 * permitted outbound transitions and state-specific validation rules.
 */
public interface DeliveryStateHandler {

    /**
     * The delivery state represented by this handler.
     */
    DeliveryState state();

    /**
     * Set of valid next states reachable directly from this state.
     */
    Set<DeliveryState> allowedNext();

    /**
     * Validates transition preconditions (guards, required proof, or failure reasons)
     * before transitioning from this state.
     *
     * @param request the transition request payload
     * @param session the current delivery session entity
     */
    void validate(TransitionDeliveryRequest request, DeliverySession session);
}

