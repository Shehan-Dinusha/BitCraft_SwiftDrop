package com.swiftdrop.delivery.statemachine;

import com.swiftdrop.contracts.enums.DeliveryState;
import com.swiftdrop.delivery.domain.entity.DeliverySession;
import com.swiftdrop.delivery.exception.InvalidStateTransitionException;

/**
 * The two routes a parcel can take.
 *
 * <ul>
 *   <li>Same district: one courier, PICKED_UP → IN_TRANSIT → OUT_FOR_DELIVERY → DELIVERED.</li>
 *   <li>Different districts, through both districts' hubs: PICKED_UP → AT_ORIGIN_HUB (the first courier
 *       hands it in) → IN_TRANSIT (the pickup district's dispatcher marks it Outbound) →
 *       AT_DESTINATION_HUB (the drop-off district's dispatcher accepts it) → OUT_FOR_DELIVERY (a courier
 *       of the drop-off district collects it from the hub) → DELIVERED.</li>
 * </ul>
 *
 * Sessions from before districts were recorded follow the same-district route.
 */
public final class HubRoute {

    private HubRoute() {
    }

    public static boolean crossesDistricts(DeliverySession session) {
        return session.getPickupDistrict() != null && session.getDeliveryDistrict() != null
                && session.getPickupDistrict() != session.getDeliveryDistrict();
    }

    /** Rejects a step that belongs to the other route. */
    public static void requireRoute(DeliveryState from, DeliveryState to, DeliverySession session) {
        boolean crosses = crossesDistricts(session);
        if (from == DeliveryState.PICKED_UP && to == DeliveryState.IN_TRANSIT && crosses) {
            throw new InvalidStateTransitionException(
                    "This parcel goes to another district: hand it in at the hub first");
        }
        if (from == DeliveryState.PICKED_UP && to == DeliveryState.AT_ORIGIN_HUB && !crosses) {
            throw new InvalidStateTransitionException(
                    "This parcel stays in the district: take it straight to the recipient");
        }
        if (from == DeliveryState.IN_TRANSIT && to == DeliveryState.OUT_FOR_DELIVERY && crosses) {
            throw new InvalidStateTransitionException(
                    "The drop-off district's hub must accept this parcel before it goes out for delivery");
        }
        if (from == DeliveryState.IN_TRANSIT && to == DeliveryState.AT_DESTINATION_HUB && !crosses) {
            throw new InvalidStateTransitionException("This parcel does not go through a hub");
        }
        if (from == DeliveryState.AT_DESTINATION_HUB && to == DeliveryState.OUT_FOR_DELIVERY
                && session.getCourierId() == null) {
            throw new InvalidStateTransitionException(
                    "Assign a courier of this district before the parcel leaves the hub");
        }
    }
}
