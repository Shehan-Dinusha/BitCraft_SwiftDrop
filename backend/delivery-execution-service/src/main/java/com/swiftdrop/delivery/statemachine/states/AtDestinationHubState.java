package com.swiftdrop.delivery.statemachine.states;

import com.swiftdrop.contracts.enums.DeliveryState;
import com.swiftdrop.delivery.domain.entity.DeliverySession;
import com.swiftdrop.delivery.dto.TransitionDeliveryRequest;
import com.swiftdrop.delivery.statemachine.DeliveryStateHandler;
import com.swiftdrop.delivery.statemachine.HubRoute;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/**
 * Accepted at the drop-off district's hub. Its dispatcher assigns a courier of the district, who
 * collects it (OUT_FOR_DELIVERY).
 */
@Component
public class AtDestinationHubState implements DeliveryStateHandler {

    @Override
    public DeliveryState state() {
        return DeliveryState.AT_DESTINATION_HUB;
    }

    @Override
    public Set<DeliveryState> allowedNext() {
        return EnumSet.of(DeliveryState.OUT_FOR_DELIVERY, DeliveryState.FAILED);
    }

    @Override
    public void validate(TransitionDeliveryRequest request, DeliverySession session) {
        HubRoute.requireRoute(DeliveryState.AT_DESTINATION_HUB, request.targetState(), session);
    }
}
