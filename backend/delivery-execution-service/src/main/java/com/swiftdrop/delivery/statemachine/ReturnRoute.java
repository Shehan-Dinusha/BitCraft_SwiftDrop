package com.swiftdrop.delivery.statemachine;

import com.swiftdrop.contracts.enums.DeliveryFailureReason;
import com.swiftdrop.contracts.enums.DeliveryState;
import com.swiftdrop.delivery.domain.entity.DeliverySession;
import com.swiftdrop.delivery.exception.InvalidStateTransitionException;

/**
 * What happens to a parcel that could not be delivered. Its courier brings it to their district's hub
 * (HELD_AT_HUB). The dispatcher there sends it out again, or back to the sender: through the sender's hub
 * when the districts differ. A parcel gets two delivery attempts; one the recipient refused gets no second.
 * A lost or damaged parcel is final (FAILED) wherever it happens.
 */
public final class ReturnRoute {

    private ReturnRoute() {
    }

    /** "Failed" means the attempt failed, unless the parcel was never collected, or is lost or damaged. */
    public static DeliveryState resolve(DeliveryState from, DeliveryState requested, DeliveryFailureReason reason,
                                        DeliverySession session) {
        if (requested == DeliveryState.ATTEMPT_FAILED && reason == null) {
            throw new InvalidStateTransitionException("Say why the delivery failed");
        }
        if (requested != DeliveryState.FAILED && requested != DeliveryState.ATTEMPT_FAILED) {
            return requested;
        }
        if (reason != null && reason.isLoss()) {
            return DeliveryState.FAILED;
        }
        return switch (from) {
            case OUT_FOR_DELIVERY -> DeliveryState.ATTEMPT_FAILED;
            case PICKED_UP, IN_TRANSIT -> {
                if (HubRoute.crossesDistricts(session)) {
                    throw new InvalidStateTransitionException("This parcel goes to the hub. Only a lost or damaged one is reported here");
                }
                yield DeliveryState.ATTEMPT_FAILED;
            }
            case OUT_FOR_RETURN -> throw new InvalidStateTransitionException(
                    "If the sender cannot take it, bring it back to the hub");
            case AT_ORIGIN_HUB, AT_DESTINATION_HUB, ATTEMPT_FAILED, HELD_AT_HUB, RETURN_IN_TRANSIT, RETURN_AT_HUB ->
                    throw new InvalidStateTransitionException("Only a lost or damaged parcel is reported as failed here");
            default -> DeliveryState.FAILED;
        };
    }

    public static void requireRoute(DeliveryState from, DeliveryState to, DeliverySession session) {
        if (from != DeliveryState.HELD_AT_HUB) {
            return;
        }
        boolean crosses = HubRoute.crossesDistricts(session);
        if (to == DeliveryState.AT_DESTINATION_HUB) {
            DeliveryFailureReason last = lastReason(session);
            int attempts = session.getAttemptCount() == null ? 0 : session.getAttemptCount();
            if (!DeliveryFailureReason.canRetry(attempts, last)) {
                throw new InvalidStateTransitionException(last != null && last.isRefusal()
                        ? "The recipient refused this parcel. Return it to the sender"
                        : "This parcel was tried " + attempts + " times. Return it to the sender");
            }
        }
        if (to == DeliveryState.RETURN_IN_TRANSIT && !crosses) {
            throw new InvalidStateTransitionException("The sender is in this district: return it from this hub");
        }
        if (to == DeliveryState.RETURN_AT_HUB && crosses) {
            throw new InvalidStateTransitionException("The sender's hub accepts this parcel when it arrives");
        }
    }

    private static DeliveryFailureReason lastReason(DeliverySession session) {
        try {
            return session.getFailedReason() == null ? null : DeliveryFailureReason.valueOf(session.getFailedReason());
        } catch (IllegalArgumentException freeText) {
            return null;
        }
    }
}
