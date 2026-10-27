package com.swiftdrop.delivery.service;

import com.swiftdrop.contracts.enums.DeliveryState;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.events.delivery.LocationUpdatedEvent;
import com.swiftdrop.contracts.events.dispatch.CourierAssignedEvent;
import com.swiftdrop.delivery.domain.entity.DeliverySession;
import com.swiftdrop.delivery.domain.entity.TrackingBreadcrumb;
import com.swiftdrop.delivery.domain.repository.DeliverySessionRepository;
import com.swiftdrop.delivery.domain.repository.TrackingBreadcrumbRepository;
import com.swiftdrop.delivery.dto.CourierLocationUpdatePayload;
import com.swiftdrop.delivery.dto.DeliverySessionResponse;
import com.swiftdrop.delivery.dto.TrackingBreadcrumbResponse;
import com.swiftdrop.delivery.dto.TransitionDeliveryRequest;
import com.swiftdrop.delivery.exception.DeliveryNotFoundException;
import com.swiftdrop.delivery.exception.InvalidStateTransitionException;
import com.swiftdrop.delivery.messaging.publisher.DeliveryEventPublisher;
import com.swiftdrop.delivery.statemachine.DeliveryStateMachine;
import com.swiftdrop.delivery.statemachine.actions.AuditTransitionAction;
import com.swiftdrop.delivery.statemachine.actions.PublishStateTransitionAction;
import com.swiftdrop.delivery.statemachine.guards.ProofOfDeliveryGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class DeliveryExecutionService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryExecutionService.class);

    private final DeliverySessionRepository deliverySessionRepository;
    private final TrackingBreadcrumbRepository breadcrumbRepository;
    private final DeliveryStateMachine stateMachine;
    private final ProofOfDeliveryGuard proofOfDeliveryGuard;
    private final AuditTransitionAction auditTransitionAction;
    private final PublishStateTransitionAction publishStateTransitionAction;
    private final DeliveryEventPublisher eventPublisher;

    public DeliveryExecutionService(
            DeliverySessionRepository deliverySessionRepository,
            TrackingBreadcrumbRepository breadcrumbRepository,
            DeliveryStateMachine stateMachine,
            ProofOfDeliveryGuard proofOfDeliveryGuard,
            AuditTransitionAction auditTransitionAction,
            PublishStateTransitionAction publishStateTransitionAction,
            DeliveryEventPublisher eventPublisher) {
        this.deliverySessionRepository = deliverySessionRepository;
        this.breadcrumbRepository = breadcrumbRepository;
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
                    newSession.setTrackingNumber("SD-" + event.orderId().toString().substring(0, 8).toUpperCase());
                    newSession.setCurrentState(DeliveryState.CREATED);
                    newSession.setStatus("ACTIVE");
                    return newSession;
                });

        DeliveryState prevState = session.getCurrentState();
        session.setCourierId(event.courierId());
        session.setCourierName(event.courierName());
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
                "Courier assigned by dispatch engine",
                null,
                null
        );

        publishStateTransitionAction.execute(
                session.getDeliveryId(),
                session.getOrderId(),
                session.getTrackingNumber(),
                session.getCourierId(),
                prevState,
                DeliveryState.ASSIGNED,
                "Courier assigned",
                session.getPickupDistrict(),
                session.getDeliveryDistrict(),
                null
        );

        return session;
    }

    @Transactional
    public DeliverySession transition(UUID deliveryId, TransitionDeliveryRequest request) {
        log.info("Attempting delivery transition for deliveryId: {} to targetState: {}",
                deliveryId, request.targetState());

        DeliverySession session = deliverySessionRepository.findByDeliveryId(deliveryId)
                .orElseThrow(() -> new DeliveryNotFoundException("Delivery session not found: " + deliveryId));

        DeliveryState fromState = session.getCurrentState();
        DeliveryState toState = request.targetState();

        if (!stateMachine.canTransition(fromState, toState)) {
            throw new InvalidStateTransitionException(
                    "Invalid delivery FSM transition from " + fromState + " to " + toState);
        }

        // Enforce Proof of Delivery Guard before transitioning to DELIVERED
        if (toState == DeliveryState.DELIVERED) {
            if (!proofOfDeliveryGuard.isSatisfied(deliveryId)) {
                throw new InvalidStateTransitionException(
                        "Proof of delivery must be verified with recipient OTP or signature before marking delivery as DELIVERED");
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
        }

        if (request.latitude() != null) session.setCurrentLatitude(request.latitude());
        if (request.longitude() != null) session.setCurrentLongitude(request.longitude());

        session.setPreviousState(fromState);
        session.setCurrentState(toState);
        session = deliverySessionRepository.save(session);

        auditTransitionAction.execute(
                session.getDeliveryId(),
                session.getOrderId(),
                fromState,
                toState,
                "COURIER_APP",
                request.actorRole() != null ? request.actorRole() : "COURIER",
                request.notes(),
                request.latitude(),
                request.longitude()
        );

        publishStateTransitionAction.execute(
                session.getDeliveryId(),
                session.getOrderId(),
                session.getTrackingNumber(),
                session.getCourierId(),
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

    @Transactional
    public TrackingBreadcrumb recordLocation(UUID deliveryId, CourierLocationUpdatePayload payload) {
        DeliverySession session = deliverySessionRepository.findByDeliveryId(deliveryId)
                .orElseThrow(() -> new DeliveryNotFoundException("Delivery session not found: " + deliveryId));

        TrackingBreadcrumb breadcrumb = new TrackingBreadcrumb();
        breadcrumb.setDeliveryId(deliveryId);
        breadcrumb.setCourierId(session.getCourierId() != null ? session.getCourierId() : UUID.randomUUID());
        breadcrumb.setLatitude(BigDecimal.valueOf(payload.latitude()));
        breadcrumb.setLongitude(BigDecimal.valueOf(payload.longitude()));
        breadcrumb.setBearingDegrees(BigDecimal.valueOf(payload.bearingDegrees()));
        breadcrumb.setSpeedKmh(BigDecimal.valueOf(payload.speedKmh()));
        breadcrumb.setAccuracy(BigDecimal.valueOf(payload.accuracy()));
        breadcrumb.setRecordedAt(Instant.now());
        breadcrumb = breadcrumbRepository.save(breadcrumb);

        session.setCurrentLatitude(BigDecimal.valueOf(payload.latitude()));
        session.setCurrentLongitude(BigDecimal.valueOf(payload.longitude()));
        deliverySessionRepository.save(session);

        eventPublisher.publishLocationUpdated(new LocationUpdatedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                deliveryId,
                session.getCourierId(),
                payload.latitude(),
                payload.longitude(),
                payload.bearingDegrees(),
                payload.speedKmh(),
                payload.accuracy(),
                Instant.now()
        ));

        return breadcrumb;
    }

    @Transactional(readOnly = true)
    public DeliverySessionResponse getDeliveryById(UUID deliveryId) {
        return deliverySessionRepository.findByDeliveryId(deliveryId)
                .map(DeliverySessionResponse::fromEntity)
                .orElseThrow(() -> new DeliveryNotFoundException("Delivery session not found: " + deliveryId));
    }

    @Transactional(readOnly = true)
    public DeliverySessionResponse getDeliveryByOrderId(UUID orderId) {
        return deliverySessionRepository.findByOrderId(orderId)
                .map(DeliverySessionResponse::fromEntity)
                .orElseThrow(() -> new DeliveryNotFoundException("Delivery session not found for order: " + orderId));
    }

    @Transactional(readOnly = true)
    public DeliverySessionResponse getDeliveryByTrackingNumber(String trackingNumber) {
        return deliverySessionRepository.findByTrackingNumber(trackingNumber)
                .map(DeliverySessionResponse::fromEntity)
                .orElseThrow(() -> new DeliveryNotFoundException("Delivery session not found for tracking: " + trackingNumber));
    }

    @Transactional(readOnly = true)
    public List<DeliverySessionResponse> getDeliveriesByCourier(UUID courierId, DeliveryState state) {
        List<DeliverySession> list;
        if (state != null) {
            list = deliverySessionRepository.findByCourierIdAndCurrentState(courierId, state);
        } else {
            list = deliverySessionRepository.findByCourierId(courierId);
        }
        return list.stream().map(DeliverySessionResponse::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public List<TrackingBreadcrumbResponse> getBreadcrumbs(UUID deliveryId) {
        return breadcrumbRepository.findTop50ByDeliveryIdOrderByRecordedAtDesc(deliveryId).stream()
                .map(TrackingBreadcrumbResponse::fromEntity)
                .toList();
    }
}
