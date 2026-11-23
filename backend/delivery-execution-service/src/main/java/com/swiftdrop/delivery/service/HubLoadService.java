package com.swiftdrop.delivery.service;

import com.swiftdrop.contracts.enums.DeliveryState;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.security.CallerContext;
import com.swiftdrop.delivery.domain.entity.DeliverySession;
import com.swiftdrop.delivery.domain.repository.DeliverySessionRepository;
import com.swiftdrop.delivery.dto.LoadParcelsRequest;
import com.swiftdrop.delivery.dto.TransitionDeliveryRequest;
import com.swiftdrop.delivery.exception.InvalidStateTransitionException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The courier loads, in one go, the parcels waiting for them at the hub before leaving on their run:
 * deliveries go out for delivery, returns go out to the sender. All of them or none.
 */
@Service
public class HubLoadService {

    private final DeliverySessionRepository sessions;
    private final DeliveryExecutionService deliveries;
    private final TransactionTemplate transaction;

    public HubLoadService(DeliverySessionRepository sessions, DeliveryExecutionService deliveries,
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
}
