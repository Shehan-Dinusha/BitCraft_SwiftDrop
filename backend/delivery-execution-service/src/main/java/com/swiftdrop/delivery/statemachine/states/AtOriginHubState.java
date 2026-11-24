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
 * At the pickup district's hub, handed in by the first courier. Going to another district, its dispatcher marks
 * it Outbound (IN_TRANSIT) towards the drop-off district's hub; staying in the district, the same dispatcher sorts
 * it for delivery (AT_DESTINATION_HUB), for a courier's next run.
 */
@Component
public class AtOriginHubState implements DeliveryStateHandler {

    @Override
    public DeliveryState state() {
        return DeliveryState.AT_ORIGIN_HUB;
    }

    @Override
    public Set<DeliveryState> allowedNext() {
        return EnumSet.of(DeliveryState.IN_TRANSIT, DeliveryState.AT_DESTINATION_HUB, DeliveryState.FAILED);
    }

    @Override
    public void validate(TransitionDeliveryRequest request, DeliverySession session) {
        // Who may take the step is checked by the service (the pickup district's dispatcher).
        HubRoute.requireRoute(DeliveryState.AT_ORIGIN_HUB, request.targetState(), session);
    }
}
