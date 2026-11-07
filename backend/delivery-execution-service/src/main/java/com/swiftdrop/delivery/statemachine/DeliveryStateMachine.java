package com.swiftdrop.delivery.statemachine;

import com.swiftdrop.contracts.enums.DeliveryState;
import com.swiftdrop.delivery.exception.InvalidStateTransitionException;
import com.swiftdrop.delivery.statemachine.states.AssignedState;
import com.swiftdrop.delivery.statemachine.states.CancelledState;
import com.swiftdrop.delivery.statemachine.states.CreatedState;
import com.swiftdrop.delivery.statemachine.states.DeliveredState;
import com.swiftdrop.delivery.statemachine.states.FailedState;
import com.swiftdrop.delivery.statemachine.states.InTransitState;
import com.swiftdrop.delivery.statemachine.states.OutForDeliveryState;
import com.swiftdrop.delivery.statemachine.states.PickedUpState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Finite State Machine orchestrator for Delivery Sessions.
 * Delegates valid transition definitions and checks to individual {@link DeliveryStateHandler} components
 * (State Pattern).
 */
@Component
public class DeliveryStateMachine {

    private final Map<DeliveryState, DeliveryStateHandler> handlers;

    /**
     * Default constructor used in standalone unit testing.
     */
    public DeliveryStateMachine() {
        this(List.of(
                new CreatedState(),
                new AssignedState(),
                new PickedUpState(),
                new InTransitState(),
                new OutForDeliveryState(),
                new DeliveredState(),
                new FailedState(),
                new CancelledState()
        ));
    }

    /**
     * Spring-injected constructor collecting all registered state handlers.
     */
    @Autowired
    public DeliveryStateMachine(List<DeliveryStateHandler> handlerList) {
        this.handlers = handlerList.stream()
                .collect(Collectors.toMap(
                        DeliveryStateHandler::state,
                        Function.identity(),
                        (existing, replacement) -> existing,
                        () -> new EnumMap<>(DeliveryState.class)
                ));
    }

    /**
     * Returns the state handler responsible for the given delivery state.
     */
    public DeliveryStateHandler getHandler(DeliveryState state) {
        DeliveryStateHandler handler = handlers.get(state);
        if (handler == null) {
            throw new IllegalStateException("No state handler registered for state: " + state);
        }
        return handler;
    }

    /**
     * Determines whether transitioning directly from one state to another is permitted.
     */
    public boolean canTransition(DeliveryState from, DeliveryState to) {
        if (from == null || to == null) {
            return false;
        }
        DeliveryStateHandler handler = handlers.get(from);
        return handler != null && handler.allowedNext().contains(to);
    }

    /**
     * Transitions from current state to target state, or throws {@link InvalidStateTransitionException}.
     */
    public DeliveryState transition(DeliveryState from, DeliveryState to) {
        if (!canTransition(from, to)) {
            throw new InvalidStateTransitionException("Invalid delivery transition: " + from + " -> " + to);
        }
        return to;
    }
}
