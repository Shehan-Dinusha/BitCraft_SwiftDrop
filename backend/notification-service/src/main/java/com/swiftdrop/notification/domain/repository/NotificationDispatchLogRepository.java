package com.swiftdrop.notification.domain.repository;

import com.swiftdrop.notification.domain.entity.NotificationDispatchLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationDispatchLogRepository extends JpaRepository<NotificationDispatchLog, Long> {
    Optional<NotificationDispatchLog> findByLogId(UUID logId);
    List<NotificationDispatchLog> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId);
    List<NotificationDispatchLog> findAllByOrderByCreatedAtDesc();
    List<NotificationDispatchLog> findByRecipientIdAndChannelOrderByCreatedAtDesc(UUID recipientId, String channel);
    List<NotificationDispatchLog> findByRecipientIdAndStatusOrderByCreatedAtDesc(UUID recipientId, String status);
    List<NotificationDispatchLog> findByStatusOrderByCreatedAtDesc(String status);
}
