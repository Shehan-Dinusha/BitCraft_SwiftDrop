package com.swiftdrop.delivery.statemachine;

import com.swiftdrop.contracts.enums.DeliveryState;
import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.delivery.domain.entity.DeliverySession;
import com.swiftdrop.delivery.exception.InvalidStateTransitionException;

/**
 * The three routes a parcel can take.
 *
 * <ul>
 *   <li>Same day, same district: one courier, PICKED_UP → IN_TRANSIT → OUT_FOR_DELIVERY → DELIVERED.</li>
 *   <li>Standard or Express, same district, through its hub: PICKED_UP → AT_ORIGIN_HUB (the courier hands it in)
 *       → AT_DESTINATION_HUB (the district's dispatcher sorts it for delivery) → OUT_FOR_DELIVERY (a courier
 *       collects it on their next run) → DELIVERED.</li>
 *   <li>Different districts, through both districts' hubs: PICKED_UP → AT_ORIGIN_HUB (the first courier
 *       hands it in) → IN_TRANSIT (the pickup district's dispatcher marks it Outbound) →
 *       AT_DESTINATION_HUB (the drop-off district's dispatcher accepts it) → OUT_FOR_DELIVERY (a courier
 *       of the drop-off district collects it from the hub) → DELIVERED.</li>
 * </ul>
 *
 * Sessions from before districts or the speed were recorded follow the same-district, straight route.
 *
 * <p>A parcel the courier carries but did not deliver today goes back to the hub ({@link #broughtBack}): from
 * there it goes out on another run. That is not a delivery attempt.</p>
 */
public final class HubRoute {

    private HubRoute() {
    }

    public static boolean crossesDistricts(DeliverySession session) {
        return session.getPickupDistrict() != null && session.getDeliveryDistrict() != null
                && session.getPickupDistrict() != session.getDeliveryDistrict();
    }

    /**
     * Handed in at a hub after pickup: anything going to another district, and Standard or Express within one.
     * Only Same day (and sessions without a recorded speed) go straight to the recipient.
     */
    public static boolean goesViaHub(DeliverySession session) {
        return crossesDistricts(session)
                || (session.getOrderSpeed() != null && session.getOrderSpeed() != OrderSpeed.SAME_DAY);
    }

    /**
     * The courier brings back to the hub a parcel they did not get to today: out for delivery, or a Same day parcel
     * still with them. Between districts, IN_TRANSIT to AT_DESTINATION_HUB is the drop-off hub accepting it instead.
     */
    public static boolean broughtBack(DeliveryState from, DeliveryState to, DeliverySession session) {
        if (to != DeliveryState.AT_DESTINATION_HUB) {
            return false;
        }
        return from == DeliveryState.OUT_FOR_DELIVERY
                || (from == DeliveryState.PICKED_UP && !goesViaHub(session))
                || (from == DeliveryState.IN_TRANSIT && !crossesDistricts(session));
    }

    /** Rejects a step that belongs to another route. */
    public static void requireRoute(DeliveryState from, DeliveryState to, DeliverySession session) {
        boolean crosses = crossesDistricts(session);
        boolean viaHub = goesViaHub(session);
        if (from == DeliveryState.PICKED_UP && to == DeliveryState.IN_TRANSIT && viaHub) {
            throw new InvalidStateTransitionException(crosses
                    ? "This parcel goes to another district: hand it in at the hub first"
                    : "This parcel goes through the hub: hand it in there first");
        }
        if (from == DeliveryState.PICKED_UP && to == DeliveryState.AT_ORIGIN_HUB && !viaHub) {
            throw new InvalidStateTransitionException(
                    "This is a same day parcel: take it straight to the recipient");
        }
        if (from == DeliveryState.AT_ORIGIN_HUB && to == DeliveryState.IN_TRANSIT && !crosses) {
            throw new InvalidStateTransitionException(
                    "This parcel stays in the district: sort it for delivery here");
        }
        if (from == DeliveryState.AT_ORIGIN_HUB && to == DeliveryState.AT_DESTINATION_HUB && crosses) {
            throw new InvalidStateTransitionException(
                    "This parcel goes to another district: mark it Outbound");
        }
        if (from == DeliveryState.IN_TRANSIT && to == DeliveryState.OUT_FOR_DELIVERY && crosses) {
            throw new InvalidStateTransitionException(
                    "The drop-off district's hub must accept this parcel before it goes out for delivery");
        }
        if (from == DeliveryState.PICKED_UP && to == DeliveryState.AT_DESTINATION_HUB && viaHub) {
            throw new InvalidStateTransitionException("This parcel goes through the hub: hand it in there");
        }
        if (from == DeliveryState.AT_DESTINATION_HUB && to == DeliveryState.OUT_FOR_DELIVERY
                && session.getCourierId() == null) {
            throw new InvalidStateTransitionException(
                    "Assign a courier of this district before the parcel leaves the hub");
        }
    }
}
