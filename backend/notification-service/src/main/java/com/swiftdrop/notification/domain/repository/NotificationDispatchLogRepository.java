package com.swiftdrop.notification.domain.repository;

import com.swiftdrop.notification.domain.entity.NotificationDispatchLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationDispatchLogRepository extends JpaRepository<NotificationDispatchLog, Long> {
    Optional<NotificationDispatchLog> findByLogId(UUID logId);
    List<NotificationDispatchLog> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId);
    List<NotificationDispatchLog> findAllByOrderByCreatedAtDesc();
    List<NotificationDispatchLog> findByRecipientIdAndChannelOrderByCreatedAtDesc(UUID recipientId, String channel);
    List<NotificationDispatchLog> findByRecipientIdAndStatusOrderByCreatedAtDesc(UUID recipientId, String status);
    List<NotificationDispatchLog> findByStatusOrderByCreatedAtDesc(String status);

    /** Used to limit how often the same alert (e.g. a failed sign-in) is emailed to one recipient. */
    boolean existsByRecipientIdAndTemplateCodeAndStatusAndCreatedAtAfter(UUID recipientId, String templateCode,
                                                                         String status, Instant after);

    /** Whether this account signed in from this device (reference) since {@code since}, ignoring failed sends. */
    boolean existsByRecipientIdAndTemplateCodeAndReferenceIdAndStatusNotAndCreatedAtAfter(
            UUID recipientId, String templateCode, String referenceId, String status, Instant since);
}
