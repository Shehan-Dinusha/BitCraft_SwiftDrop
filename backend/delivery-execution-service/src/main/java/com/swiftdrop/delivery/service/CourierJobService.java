package com.swiftdrop.delivery.service;

import com.swiftdrop.contracts.enums.DeliveryState;
import com.swiftdrop.contracts.security.CallerContext;
import com.swiftdrop.delivery.client.OrderJobClient;
import com.swiftdrop.delivery.domain.entity.DeliverySession;
import com.swiftdrop.delivery.domain.repository.DeliverySessionRepository;
import com.swiftdrop.delivery.dto.CourierJobResponse;
import com.swiftdrop.delivery.exception.DeliveryNotFoundException;
import com.swiftdrop.delivery.security.DeliveryAccessPolicy;
import com.swiftdrop.delivery.statemachine.HubRoute;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * The job as the courier carrying it sees it. A courier gets only their own leg: the first courier of a
 * cross-district parcel visits the sender and the hub, so they never get the recipient; the last one
 * never gets the sender. Once a delivery is over nobody gets contacts through here.
 */
@Service
public class CourierJobService {

    private static final Set<DeliveryState> BEFORE_PICKUP = EnumSet.of(DeliveryState.CREATED, DeliveryState.ASSIGNED);
    private static final Set<DeliveryState> FINISHED =
            EnumSet.of(DeliveryState.DELIVERED, DeliveryState.FAILED, DeliveryState.CANCELLED);
    private static final Set<DeliveryState> LAST_MILE =
            EnumSet.of(DeliveryState.AT_DESTINATION_HUB, DeliveryState.OUT_FOR_DELIVERY);

    private final DeliverySessionRepository deliverySessionRepository;
    private final OrderJobClient orderJobClient;

    public CourierJobService(DeliverySessionRepository deliverySessionRepository, OrderJobClient orderJobClient) {
        this.deliverySessionRepository = deliverySessionRepository;
        this.orderJobClient = orderJobClient;
    }

    /** Not transactional: no transaction stays open while the order service answers. */
    public CourierJobResponse getJob(UUID deliveryId, CallerContext caller) {
        DeliverySession session = deliverySessionRepository.findByDeliveryId(deliveryId)
                .orElseThrow(() -> new DeliveryNotFoundException("Delivery session not found: " + deliveryId));
        DeliveryAccessPolicy.assertCanAccess(session, caller);

        OrderJobClient.OrderJob order = orderJobClient.get(session.getOrderId());

        DeliveryState state = session.getCurrentState();
        boolean cancelled = order.cancelled() || state == DeliveryState.CANCELLED;
        boolean active = !cancelled && !FINISHED.contains(state);
        boolean beforePickup = active && BEFORE_PICKUP.contains(state);
        boolean deliversIt = active && (!HubRoute.crossesDistricts(session) || LAST_MILE.contains(state));

        return new CourierJobResponse(
                session.getDeliveryId(),
                cancelled,
                order.speed(),
                beforePickup ? order.pickup() : null,
                deliversIt ? order.dropOff() : null,
                active ? order.notes() : null,
                order.itemDescription(),
                order.lengthCm(),
                order.widthCm(),
                order.heightCm(),
                order.weightKg(),
                beforePickup ? positive(order.fee()) : null,
                deliversIt ? positive(order.codAmount()) : null,
                order.currency());
    }

    private static BigDecimal positive(BigDecimal amount) {
        return amount != null && amount.signum() > 0 ? amount : null;
    }
}
