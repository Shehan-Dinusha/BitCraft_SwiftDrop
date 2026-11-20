package com.swiftdrop.delivery.statemachine.actions;

import com.swiftdrop.contracts.enums.DeliveryState;
import com.swiftdrop.delivery.domain.entity.StateTransitionAudit;
import com.swiftdrop.delivery.domain.repository.StateTransitionAuditRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class AuditTransitionAction {

    private final StateTransitionAuditRepository auditRepository;

    public AuditTransitionAction(StateTransitionAuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    public StateTransitionAudit execute(
            UUID deliveryId,
            UUID orderId,
            DeliveryState fromState,
            DeliveryState toState,
            String triggeredBy,
            String actorRole,
            String notes) {

        StateTransitionAudit audit = new StateTransitionAudit();
        audit.setDeliveryId(deliveryId);
        audit.setOrderId(orderId);
        audit.setFromState(fromState);
        audit.setToState(toState);
        audit.setTriggeredBy(triggeredBy != null ? triggeredBy : "COURIER_APP");
        audit.setActorRole(actorRole != null ? actorRole : "COURIER");
        audit.setNotes(notes);
        audit.setCreatedAt(Instant.now());

        return auditRepository.save(audit);
    }
}
