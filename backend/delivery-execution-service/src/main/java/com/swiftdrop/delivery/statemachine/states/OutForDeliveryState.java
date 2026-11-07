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

@Component
public class OutForDeliveryState implements DeliveryStateHandler {

    private final ProofOfDeliveryGuard proofOfDeliveryGuard;

    public OutForDeliveryState() {
        this(null);
    }

    @Autowired
    public OutForDeliveryState(@Autowired(required = false) ProofOfDeliveryGuard proofOfDeliveryGuard) {
        this.proofOfDeliveryGuard = proofOfDeliveryGuard;
    }

    @Override
    public DeliveryState state() {
        return DeliveryState.OUT_FOR_DELIVERY;
    }

    @Override
    public Set<DeliveryState> allowedNext() {
        return EnumSet.of(DeliveryState.DELIVERED, DeliveryState.FAILED);
    }

    @Override
    public void validate(TransitionDeliveryRequest request, DeliverySession session) {
        if (request == null) {
            throw new InvalidStateTransitionException("Transition request must not be null");
        }

        DeliveryState target = request.targetState();
        if (target == DeliveryState.DELIVERED) {
            if (proofOfDeliveryGuard != null && session != null && session.getDeliveryId() != null) {
                if (!proofOfDeliveryGuard.isSatisfied(session.getDeliveryId())) {
                    throw new InvalidStateTransitionException(
                            "Proof of delivery must be verified with recipient OTP or signature before marking delivery as DELIVERED");
                }
            }
        } else if (target == DeliveryState.FAILED) {
            if (request.failureReason() == null) {
                throw new InvalidStateTransitionException(
                        "Delivery failure requires a valid DeliveryFailureReason code");
            }
        }
    }
}
