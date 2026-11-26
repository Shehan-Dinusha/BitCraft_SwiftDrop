package com.swiftdrop.delivery.statemachine.states;

import com.swiftdrop.contracts.enums.DeliveryState;
import com.swiftdrop.delivery.domain.entity.DeliverySession;
import com.swiftdrop.delivery.dto.TransitionDeliveryRequest;
import com.swiftdrop.delivery.exception.InvalidStateTransitionException;
import com.swiftdrop.delivery.statemachine.DeliveryStateHandler;
import com.swiftdrop.delivery.statemachine.guards.ProofOfDeliveryGuard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/**
 * OUT_FOR_DELIVERY: the courier is on the way to the recipient. It ends DELIVERED (which needs verified proof of
 * delivery), as a failed attempt or FAILED (which needs a reason code), or back at the hub (AT_DESTINATION_HUB)
 * when the courier did not get to it today, which is not an attempt.
 */
@Component
public class OutForDeliveryState implements DeliveryStateHandler {

    private final ProofOfDeliveryGuard proofOfDeliveryGuard;

    /**
     * Transition-graph-only instance for unit tests: without a guard, DELIVERED is always rejected.
     */
    public OutForDeliveryState() {
        this(null);
    }

    /** Spring constructor: the proof-of-delivery guard is mandatory. */
    @Autowired
    public OutForDeliveryState(ProofOfDeliveryGuard proofOfDeliveryGuard) {
        this.proofOfDeliveryGuard = proofOfDeliveryGuard;
    }

    @Override
    public DeliveryState state() {
        return DeliveryState.OUT_FOR_DELIVERY;
    }

    @Override
    public Set<DeliveryState> allowedNext() {
        return EnumSet.of(DeliveryState.DELIVERED, DeliveryState.ATTEMPT_FAILED, DeliveryState.FAILED,
                DeliveryState.AT_DESTINATION_HUB);
    }

    @Override
    public void validate(TransitionDeliveryRequest request, DeliverySession session) {
        if (request == null) {
            throw new InvalidStateTransitionException("Transition request must not be null");
        }

        DeliveryState target = request.targetState();
        if (target == DeliveryState.DELIVERED) {
            // Fail closed: no guard, no session or no delivery id means proof cannot be confirmed.
            boolean proven = proofOfDeliveryGuard != null && session != null && session.getDeliveryId() != null
                    && proofOfDeliveryGuard.isSatisfied(session.getDeliveryId(), session.getProofSince());
            if (!proven) {
                throw new InvalidStateTransitionException(
                        "Proof of delivery must be verified with an uploaded photo or signature before marking delivery as DELIVERED");
            }
        } else if (target == DeliveryState.FAILED) {
            if (request.failureReason() == null) {
                throw new InvalidStateTransitionException(
                        "Delivery failure requires a valid DeliveryFailureReason code");
            }
        }
    }
}
