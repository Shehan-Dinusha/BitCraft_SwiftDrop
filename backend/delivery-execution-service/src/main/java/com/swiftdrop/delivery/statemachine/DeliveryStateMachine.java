package com.swiftdrop.delivery.statemachine;

import com.swiftdrop.contracts.enums.DeliveryState;
import com.swiftdrop.delivery.exception.InvalidStateTransitionException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class DeliveryStateMachine {

    private static final Map<DeliveryState, Set<DeliveryState>> TRANSITIONS = new EnumMap<>(DeliveryState.class);

    static {
        TRANSITIONS.put(DeliveryState.CREATED, EnumSet.of(DeliveryState.ASSIGNED, DeliveryState.CANCELLED));
        TRANSITIONS.put(DeliveryState.ASSIGNED, EnumSet.of(DeliveryState.PICKED_UP, DeliveryState.FAILED, DeliveryState.CANCELLED));
        TRANSITIONS.put(DeliveryState.PICKED_UP, EnumSet.of(DeliveryState.IN_TRANSIT, DeliveryState.FAILED));
        TRANSITIONS.put(DeliveryState.IN_TRANSIT, EnumSet.of(DeliveryState.DELIVERED, DeliveryState.FAILED));
        TRANSITIONS.put(DeliveryState.DELIVERED, EnumSet.noneOf(DeliveryState.class));
        TRANSITIONS.put(DeliveryState.FAILED, EnumSet.noneOf(DeliveryState.class));
        TRANSITIONS.put(DeliveryState.CANCELLED, EnumSet.noneOf(DeliveryState.class));
    }

    public boolean canTransition(DeliveryState from, DeliveryState to) {
        if (from == null || to == null) return false;
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public DeliveryState transition(DeliveryState from, DeliveryState to) {
        if (!canTransition(from, to)) {
            throw new InvalidStateTransitionException("Invalid delivery transition: " + from + " -> " + to);
        }
        return to;
    }
}
