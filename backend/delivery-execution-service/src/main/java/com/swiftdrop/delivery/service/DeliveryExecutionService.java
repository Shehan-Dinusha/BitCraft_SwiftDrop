package com.swiftdrop.delivery.service;

import com.swiftdrop.contracts.security.ForbiddenException;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.enums.DeliveryState;
import com.swiftdrop.contracts.security.CallerContext;
import com.swiftdrop.contracts.security.UnauthenticatedCallerException;
import com.swiftdrop.contracts.events.dispatch.CourierAssignedEvent;
import com.swiftdrop.delivery.domain.entity.DeliverySession;
import com.swiftdrop.delivery.domain.repository.DeliverySessionRepository;
import com.swiftdrop.delivery.dto.DeliverySessionResponse;
import com.swiftdrop.delivery.dto.TransitionDeliveryRequest;
import com.swiftdrop.delivery.exception.DeliveryNotFoundException;
import com.swiftdrop.delivery.exception.InvalidStateTransitionException;
import com.swiftdrop.delivery.messaging.publisher.DeliveryEventPublisher;
import com.swiftdrop.delivery.security.DeliveryAccessPolicy;
import com.swiftdrop.delivery.statemachine.DeliveryStateMachine;
import com.swiftdrop.delivery.statemachine.actions.AuditTransitionAction;
import com.swiftdrop.delivery.statemachine.actions.PublishStateTransitionAction;
import com.swiftdrop.delivery.statemachine.guards.ProofOfDeliveryGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

@Service
public class DeliveryExecutionService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryExecutionService.class);

    private final DeliverySessionRepository deliverySessionRepository;
    private final DeliveryStateMachine stateMachine;
    private final ProofOfDeliveryGuard proofOfDeliveryGuard;
    private final AuditTransitionAction auditTransitionAction;
    private final PublishStateTransitionAction publishStateTransitionAction;
    private final DeliveryEventPublisher eventPublisher;

    public DeliveryExecutionService(
            DeliverySessionRepository deliverySessionRepository,
            DeliveryStateMachine stateMachine,
            ProofOfDeliveryGuard proofOfDeliveryGuard,
            AuditTransitionAction auditTransitionAction,
            PublishStateTransitionAction publishStateTransitionAction,
            DeliveryEventPublisher eventPublisher) {
        this.deliverySessionRepository = deliverySessionRepository;
        this.stateMachine = stateMachine;
        this.proofOfDeliveryGuard = proofOfDeliveryGuard;
        this.auditTransitionAction = auditTransitionAction;
        this.publishStateTransitionAction = publishStateTransitionAction;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public DeliverySession handleCourierAssigned(CourierAssignedEvent event) {
        log.info("Handling CourierAssignedEvent for order: {}, courier: {}", event.orderId(), event.courierId());

        DeliverySession session = deliverySessionRepository.findByOrderId(event.orderId())
                .orElseGet(() -> {
                    DeliverySession newSession = new DeliverySession();
                    newSession.setDeliveryId(UUID.randomUUID());
                    newSession.setOrderId(event.orderId());
                    // Fallback only for old events without a tracking number.
                    newSession.setTrackingNumber("SD-" + event.orderId().toString().substring(0, 8).toUpperCase());
                    newSession.setCurrentState(DeliveryState.CREATED);
                    newSession.setStatus("ACTIVE");
                    return newSession;
                });

        DeliveryState prevState = session.getCurrentState();
        if (prevState == DeliveryState.AT_DESTINATION_HUB) {
            return assignLastMileCourier(session, event);
        }
        session.setCourierId(event.courierId());
        session.setCourierName(event.courierName());
        if (event.customerId() != null) {
            session.setCustomerId(event.customerId());
        }
        // The real tracking number keeps live tracking (keyed by it) working, and the
        // districts are required by analytics, which rejects status events without one.
        if (event.trackingNumber() != null && !event.trackingNumber().isBlank()) {
            session.setTrackingNumber(event.trackingNumber());
        }
        if (event.pickupDistrict() != null) {
            session.setPickupDistrict(event.pickupDistrict());
        }
        if (event.deliveryDistrict() != null) {
            session.setDeliveryDistrict(event.deliveryDistrict());
        }
        session.setPreviousState(prevState);
        session.setCurrentState(DeliveryState.ASSIGNED);
        session = deliverySessionRepository.save(session);

        auditTransitionAction.execute(
                session.getDeliveryId(),
                session.getOrderId(),
                prevState,
                DeliveryState.ASSIGNED,
                "DISPATCH_ASSIGNMENT_EVENT",
                "SYSTEM",
                "Courier assigned by dispatch engine"
        );

        publishStateTransitionAction.execute(
                session.getDeliveryId(),
                session.getOrderId(),
                session.getTrackingNumber(),
                session.getCourierId(),
                session.getCustomerId(),
                prevState,
                DeliveryState.ASSIGNED,
                "Courier assigned",
                session.getPickupDistrict(),
                session.getDeliveryDistrict(),
                null
        );

        return session;
    }

    /**
     * The drop-off district's dispatcher assigned (or changed) the courier who collects the parcel from
     * their hub. The parcel stays AT_DESTINATION_HUB until that courier collects it; customers see no
     * change, so no status event is published.
     */
    private DeliverySession assignLastMileCourier(DeliverySession session, CourierAssignedEvent event) {
        session.setCourierId(event.courierId());
        session.setCourierName(event.courierName());
        session = deliverySessionRepository.save(session);
        auditTransitionAction.execute(session.getDeliveryId(), session.getOrderId(),
                DeliveryState.AT_DESTINATION_HUB, DeliveryState.AT_DESTINATION_HUB,
                "DISPATCH_ASSIGNMENT_EVENT", "SYSTEM", "Last-mile courier assigned at the drop-off hub");
        return session;
    }

    /**
     * Who may take a hub step. The pickup district's dispatcher marks a parcel Outbound; the drop-off
     * district's dispatcher accepts it at their hub; a courier never does either. Handing in at the
     * origin hub is the first courier's step (their dispatcher may record it too). A system admin may
     * take any step. Internal callers (no caller) are not checked.
     */
    private static void requireHubActor(DeliveryState from, DeliveryState to, DeliverySession session,
                                        CallerContext caller) {
        if (caller == null || caller.role() == UserRole.SYSTEM_ADMIN) {
            return;
        }
        boolean outbound = from == DeliveryState.AT_ORIGIN_HUB && to == DeliveryState.IN_TRANSIT;
        boolean accept = from == DeliveryState.IN_TRANSIT && to == DeliveryState.AT_DESTINATION_HUB;
        boolean handIn = from == DeliveryState.PICKED_UP && to == DeliveryState.AT_ORIGIN_HUB;
        if (outbound && !isDispatcherOf(caller, session.getPickupDistrict())) {
            throw new ForbiddenException("Only the pickup district's dispatcher can mark this parcel Outbound");
        }
        if (accept && !isDispatcherOf(caller, session.getDeliveryDistrict())) {
            throw new ForbiddenException("Only the drop-off district's dispatcher can accept this parcel");
        }
        if (handIn && caller.role() == UserRole.DISPATCHER && !isDispatcherOf(caller, session.getPickupDistrict())) {
            throw new ForbiddenException("Only the pickup district's hub can receive this parcel");
        }
    }

    private static boolean isDispatcherOf(CallerContext caller, SriLankanDistrict district) {
        return caller.role() == UserRole.DISPATCHER && district != null && caller.districtId() == district;
    }

    @Transactional
    public DeliverySession transition(UUID deliveryId, TransitionDeliveryRequest request) {
        return transition(deliveryId, request, null);
    }

    @Transactional
    public DeliverySession transition(UUID deliveryId, TransitionDeliveryRequest request, CallerContext caller) {
        log.info("Attempting delivery transition for deliveryId: {} to targetState: {}",
                deliveryId, request.targetState());

        DeliverySession session = deliverySessionRepository.findByDeliveryId(deliveryId)
                .orElseThrow(() -> new DeliveryNotFoundException("Delivery session not found: " + deliveryId));

        if (caller != null) {
            DeliveryAccessPolicy.assertCanAccess(session, caller);
        }

        DeliveryState fromState = session.getCurrentState();
        DeliveryState toState = request.targetState();

        if (!stateMachine.canTransition(fromState, toState)) {
            throw new InvalidStateTransitionException(
                    "Invalid delivery FSM transition from " + fromState + " to " + toState);
        }

        // Delegate state-specific guard and precondition validation to the handler
        stateMachine.getHandler(fromState).validate(request, session);
        requireHubActor(fromState, toState, session, caller);

        // Enforce Proof of Delivery Guard before transitioning to DELIVERED
        if (toState == DeliveryState.DELIVERED) {
            if (!proofOfDeliveryGuard.isSatisfied(deliveryId)) {
                throw new InvalidStateTransitionException(
                        "Proof of delivery must be verified with an uploaded photo or signature before marking delivery as DELIVERED");
            }
            session.setDeliveredAt(Instant.now());
            session.setStatus("COMPLETED");
        } else if (toState == DeliveryState.PICKED_UP) {
            session.setPickupAt(Instant.now());
        } else if (toState == DeliveryState.FAILED) {
            session.setFailedReason(request.failureReason() != null
                    ? request.failureReason().name()
                    : (request.notes() != null ? request.notes() : "Delivery attempt failed"));
            session.setAttemptCount((session.getAttemptCount() != null ? session.getAttemptCount() : 0) + 1);
            session.setStatus("FAILED");
        } else if (toState == DeliveryState.CANCELLED) {
            session.setStatus("CANCELLED");
        } else if (toState == DeliveryState.AT_ORIGIN_HUB) {
            // The first courier's part ends at the hub: the parcel leaves their list and workload.
            session.setCourierId(null);
            session.setCourierName(null);
        }

        session.setPreviousState(fromState);
        session.setCurrentState(toState);
        session = deliverySessionRepository.save(session);

        auditTransitionAction.execute(
                session.getDeliveryId(),
                session.getOrderId(),
                fromState,
                toState,
                caller != null && caller.role() != UserRole.COURIER ? "OPERATIONS_CONSOLE" : "COURIER_APP",
                caller != null ? caller.role().name()
                        : (request.actorRole() != null ? request.actorRole() : "COURIER"),
                request.notes()
        );

        publishStateTransitionAction.execute(
                session.getDeliveryId(),
                session.getOrderId(),
                session.getTrackingNumber(),
                session.getCourierId(),
                session.getCustomerId(),
                fromState,
                toState,
                request.notes(),
                session.getPickupDistrict(),
                session.getDeliveryDistrict(),
                request.failureReason()
        );

        log.info("Delivery {} transitioned from {} to {}", deliveryId, fromState, toState);
        return session;
    }

    @Transactional(readOnly = true)
    public void assertCanAccess(UUID deliveryId, CallerContext caller) {
        if (caller != null) {
            DeliverySession session = deliverySessionRepository.findByDeliveryId(deliveryId)
                    .orElseThrow(() -> new DeliveryNotFoundException("Delivery session not found: " + deliveryId));
            DeliveryAccessPolicy.assertCanAccess(session, caller);
        }
    }

    @Transactional(readOnly = true)
    public DeliverySessionResponse getDeliveryById(UUID deliveryId) {
        return getDeliveryById(deliveryId, null);
    }

    @Transactional(readOnly = true)
    public DeliverySessionResponse getDeliveryById(UUID deliveryId, CallerContext caller) {
        DeliverySession session = deliverySessionRepository.findByDeliveryId(deliveryId)
                .orElseThrow(() -> new DeliveryNotFoundException("Delivery session not found: " + deliveryId));
        if (caller != null) {
            DeliveryAccessPolicy.assertCanAccess(session, caller);
        }
        return DeliverySessionResponse.fromEntity(session);
    }

    @Transactional(readOnly = true)
    public DeliverySessionResponse getDeliveryByOrderId(UUID orderId) {
        return getDeliveryByOrderId(orderId, null);
    }

    @Transactional(readOnly = true)
    public DeliverySessionResponse getDeliveryByOrderId(UUID orderId, CallerContext caller) {
        DeliverySession session = deliverySessionRepository.findByOrderId(orderId)
                .orElseThrow(() -> new DeliveryNotFoundException("Delivery session not found for order: " + orderId));
        if (caller != null) {
            DeliveryAccessPolicy.assertCanAccess(session, caller);
        }
        return DeliverySessionResponse.fromEntity(session);
    }

    @Transactional(readOnly = true)
    public DeliverySessionResponse getDeliveryByTrackingNumber(String trackingNumber) {
        return getDeliveryByTrackingNumber(trackingNumber, null);
    }

    @Transactional(readOnly = true)
    public DeliverySessionResponse getDeliveryByTrackingNumber(String trackingNumber, CallerContext caller) {
        DeliverySession session = deliverySessionRepository.findByTrackingNumber(trackingNumber)
                .orElseThrow(() -> new DeliveryNotFoundException("Delivery session not found for tracking: " + trackingNumber));
        if (caller != null) {
            DeliveryAccessPolicy.assertCanAccess(session, caller);
        }
        return DeliverySessionResponse.fromEntity(session);
    }

    @Transactional(readOnly = true)
    public List<DeliverySessionResponse> getDeliveriesByCourier(UUID courierId, DeliveryState state) {
        return getDeliveriesByCourier(courierId, state, null);
    }

    @Transactional(readOnly = true)
    public List<DeliverySessionResponse> getDeliveriesByCourier(UUID courierId, DeliveryState state, CallerContext caller) {
        if (caller != null) {
            DeliveryAccessPolicy.assertCanAccess(courierId, caller);
        }
        List<DeliverySession> list;
        if (state != null) {
            list = deliverySessionRepository.findByCourierIdAndCurrentState(courierId, state);
        } else {
            list = deliverySessionRepository.findByCourierId(courierId);
        }
        return list.stream().map(DeliverySessionResponse::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public List<DeliverySessionResponse> getMyDeliveries(UUID courierId, String dateStr) {
        LocalDate targetDate;
        boolean today = false;
        if (dateStr == null || dateStr.isBlank() || "today".equalsIgnoreCase(dateStr.trim())) {
            targetDate = LocalDate.now(ZoneId.of("Asia/Colombo"));
            today = true;
        } else if ("all".equalsIgnoreCase(dateStr.trim())) {
            return deliverySessionRepository.findByCourierIdOrderByCreatedAtAsc(courierId)
                    .stream()
                    .map(DeliverySessionResponse::fromEntity)
                    .toList();
        } else {
            try {
                targetDate = LocalDate.parse(dateStr.trim());
            } catch (DateTimeParseException ex) {
                throw new IllegalArgumentException("Invalid date format: " + dateStr + ". Expected YYYY-MM-DD or 'today'");
            }
        }

        Instant startOfDay = targetDate.atStartOfDay(ZoneId.of("Asia/Colombo")).toInstant();
        Instant endOfDay = targetDate.plusDays(1).atStartOfDay(ZoneId.of("Asia/Colombo")).toInstant();

        List<DeliverySession> sessions = today
                ? deliverySessionRepository.findTodayAndOpenByCourierId(courierId, startOfDay, endOfDay)
                : deliverySessionRepository.findByCourierIdAndDateRange(courierId, startOfDay, endOfDay);
        return sessions.stream().map(DeliverySessionResponse::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public List<DeliverySessionResponse> getMyDeliveries(CallerContext caller, String dateStr) {
        if (caller == null) {
            throw new UnauthenticatedCallerException("Caller context is required");
        }
        DeliveryAccessPolicy.assertCanAccess(caller.userId(), caller);
        return getMyDeliveries(caller.userId(), dateStr);
    }
}
