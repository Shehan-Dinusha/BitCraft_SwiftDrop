package com.swiftdrop.notification.domain.repository;

import com.swiftdrop.notification.domain.entity.InAppNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InAppNotificationRepository extends JpaRepository<InAppNotification, Long> {

    List<InAppNotification> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<InAppNotification> findByIdAndUserId(Long id, UUID userId);

    long countByUserIdAndReadFalse(UUID userId);

    @Modifying
    @Query("UPDATE InAppNotification n SET n.read = true WHERE n.userId = :userId AND n.read = false")
    int markAllReadByUserId(@Param("userId") UUID userId);
}

