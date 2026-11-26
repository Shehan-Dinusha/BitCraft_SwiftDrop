package com.swiftdrop.delivery.service;

import com.swiftdrop.contracts.enums.DeliveryState;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.security.CallerContext;
import com.swiftdrop.delivery.domain.entity.DeliverySession;
import com.swiftdrop.delivery.domain.repository.DeliverySessionRepository;
import com.swiftdrop.delivery.dto.LoadParcelsRequest;
import com.swiftdrop.delivery.dto.TransitionDeliveryRequest;
import com.swiftdrop.delivery.exception.InvalidStateTransitionException;
import com.swiftdrop.delivery.statemachine.HubRoute;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What a courier does at the hub, a batch at a time, all of them or none. In the morning they load what waits for
 * them (deliveries go out for delivery, returns out to the sender). Coming back, they hand in what they carry:
 * pickups that go through the hub, failed attempts, returns the sender could not take, and parcels they did not
 * get to today (back for another run, not an attempt).
 */
@Service
public class HubDeskService {

    private final DeliverySessionRepository sessions;
    private final DeliveryExecutionService deliveries;
    private final TransactionTemplate transaction;

    public HubDeskService(DeliverySessionRepository sessions, DeliveryExecutionService deliveries,
                          TransactionTemplate transaction) {
        this.sessions = sessions;
        this.deliveries = deliveries;
        this.transaction = transaction;
    }

    public List<DeliverySession> load(CallerContext caller, LoadParcelsRequest request) {
        caller.requireRole(UserRole.COURIER);
        List<UUID> chosen = request.deliveryIds().stream().distinct().toList();
        return transaction.execute(status -> {
            List<DeliverySession> loaded = new ArrayList<>();
            for (UUID deliveryId : chosen) {
                DeliverySession session = sessions.findByDeliveryId(deliveryId).orElse(null);
                DeliveryState target = session == null || !caller.userId().equals(session.getCourierId()) ? null
                        : switch (session.getCurrentState()) {
                            case AT_DESTINATION_HUB -> DeliveryState.OUT_FOR_DELIVERY;
                            case RETURN_AT_HUB -> DeliveryState.OUT_FOR_RETURN;
                            default -> null;
                        };
                if (target == null) {
                    throw new InvalidStateTransitionException(
                            "One of these parcels is no longer waiting for you at the hub. Refresh and try again.");
                }
                loaded.add(deliveries.transition(deliveryId,
                        new TransitionDeliveryRequest(target, "Loaded at the hub", null, "COURIER"), caller, null));
            }
            return loaded;
        });
    }

    public List<DeliverySession> handIn(CallerContext caller, LoadParcelsRequest request) {
        caller.requireRole(UserRole.COURIER);
        List<UUID> chosen = request.deliveryIds().stream().distinct().toList();
        return transaction.execute(status -> {
            List<DeliverySession> handedIn = new ArrayList<>();
            for (UUID deliveryId : chosen) {
                DeliverySession session = sessions.findByDeliveryId(deliveryId).orElse(null);
                DeliveryState target = session == null || !caller.userId().equals(session.getCourierId()) ? null
                        : handInStep(session);
                if (target == null) {
                    throw new InvalidStateTransitionException(
                            "One of these parcels is no longer with you. Refresh and try again.");
                }
                handedIn.add(deliveries.transition(deliveryId,
                        new TransitionDeliveryRequest(target, "Handed in at the hub", null, "COURIER"), caller, null));
            }
            return handedIn;
        });
    }

    /** Where a parcel the courier carries goes when they hand it in at their hub, or null when it is not theirs to. */
    static DeliveryState handInStep(DeliverySession session) {
        return switch (session.getCurrentState()) {
            case PICKED_UP -> HubRoute.goesViaHub(session) ? DeliveryState.AT_ORIGIN_HUB : DeliveryState.AT_DESTINATION_HUB;
            case IN_TRANSIT -> HubRoute.crossesDistricts(session) ? null : DeliveryState.AT_DESTINATION_HUB;
            case OUT_FOR_DELIVERY -> DeliveryState.AT_DESTINATION_HUB;
            case ATTEMPT_FAILED -> DeliveryState.HELD_AT_HUB;
            case OUT_FOR_RETURN -> DeliveryState.RETURN_AT_HUB;
            default -> null;
        };
    }
}
