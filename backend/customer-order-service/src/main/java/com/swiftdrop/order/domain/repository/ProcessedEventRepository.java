package com.swiftdrop.order.domain.repository;

import com.swiftdrop.order.domain.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {

    /**
     * Atomically claims an event id. Returns 1 if this call claimed it, 0 if it was already claimed
     * (a duplicate delivery), even when two deliveries race.
     */
    @Modifying
    @Query(value = "INSERT INTO processed_event (event_id) VALUES (:eventId) ON CONFLICT (event_id) DO NOTHING",
            nativeQuery = true)
    int claim(@Param("eventId") UUID eventId);
}
