package com.swiftdrop.notification.domain.repository;

import com.swiftdrop.notification.domain.entity.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    Optional<PushSubscription> findByEndpoint(String endpoint);

    List<PushSubscription> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId);

    boolean existsByRecipientId(UUID recipientId);

    @Modifying
    @Query("delete from PushSubscription s where s.endpoint = :endpoint and s.recipientId = :recipientId")
    int deleteByEndpointAndRecipientId(@Param("endpoint") String endpoint, @Param("recipientId") UUID recipientId);
}
