package com.swiftdrop.delivery.service;

import com.swiftdrop.contracts.enums.DeliveryState;
import com.swiftdrop.contracts.security.CallerContext;
import com.swiftdrop.delivery.client.OrderJobClient;
import com.swiftdrop.delivery.domain.entity.DeliverySession;
import com.swiftdrop.delivery.domain.repository.DeliverySessionRepository;
import com.swiftdrop.delivery.dto.TransitionDeliveryRequest;
import com.swiftdrop.delivery.exception.CashConflictException;
import com.swiftdrop.delivery.exception.DeliveryNotFoundException;
import com.swiftdrop.delivery.security.DeliveryAccessPolicy;
import com.swiftdrop.delivery.statemachine.DeliveryStateMachine;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Takes a delivery step. A pickup also takes the delivery fee from the sender: the order service says how
 * much, the request must confirm exactly that, and the step and the cash entry are saved together. The
 * recipient never pays anything.
 *
 * <p>Not transactional, so no database connection is held while the order service answers.</p>
 */
@Service
public class CourierStepService {

    private final DeliverySessionRepository deliverySessionRepository;
    private final DeliveryStateMachine stateMachine;
    private final OrderJobClient orderJobClient;
    private final DeliveryExecutionService deliveryExecutionService;

    public CourierStepService(DeliverySessionRepository deliverySessionRepository, DeliveryStateMachine stateMachine,
                              OrderJobClient orderJobClient, DeliveryExecutionService deliveryExecutionService) {
        this.deliverySessionRepository = deliverySessionRepository;
        this.stateMachine = stateMachine;
        this.orderJobClient = orderJobClient;
        this.deliveryExecutionService = deliveryExecutionService;
    }

    public DeliverySession step(UUID deliveryId, TransitionDeliveryRequest request, CallerContext caller) {
        return deliveryExecutionService.transition(deliveryId, request, caller, cashDue(deliveryId, request, caller));
    }

    private CashDue cashDue(UUID deliveryId, TransitionDeliveryRequest request, CallerContext caller) {
        if (request.targetState() != DeliveryState.PICKED_UP) {
            return null;
        }
        DeliverySession session = deliverySessionRepository.findByDeliveryId(deliveryId)
                .orElseThrow(() -> new DeliveryNotFoundException("Delivery session not found: " + deliveryId));
        DeliveryAccessPolicy.assertCanAccess(session, caller);
        if (!stateMachine.canTransition(session.getCurrentState(), DeliveryState.PICKED_UP)) {
            return null; // the transition itself answers with the right error
        }

        OrderJobClient.OrderJob order = orderJobClient.get(session.getOrderId());
        if (order.cancelled()) {
            throw new CashConflictException("This order was cancelled. Do not pick it up or collect any cash.");
        }
        BigDecimal amount = order.fee();
        if (amount == null || amount.signum() <= 0) {
            return null;
        }
        String currency = order.currency() != null ? order.currency() : "LKR";
        if (request.cashCollected() == null || request.cashCollected().compareTo(amount) != 0) {
            throw new CashConflictException("Collect %s %,.2f from the sender first, then confirm it."
                    .formatted(currency, amount));
        }
        return new CashDue(amount, currency);
    }
}
