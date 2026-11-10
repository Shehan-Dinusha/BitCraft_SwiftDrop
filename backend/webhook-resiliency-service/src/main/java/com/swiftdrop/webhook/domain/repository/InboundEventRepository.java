package com.swiftdrop.webhook.domain.repository;

import com.swiftdrop.webhook.domain.entity.InboundEvent;
import com.swiftdrop.webhook.domain.entity.InboundEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Read access to the inbox. Writes go through {@link IngestionJdbcRepository}. */
@Repository
public interface InboundEventRepository extends JpaRepository<InboundEvent, UUID> {

    List<InboundEvent> findByOrderIdOrderByOccurredAtAsc(UUID orderId);

    /** Diagnosis of parked events, e.g. after an {@code order.created} was lost upstream. */
    List<InboundEvent> findByProcessingStatusOrderByReceivedAtAsc(InboundEventStatus processingStatus);
}
