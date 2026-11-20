package com.swiftdrop.notification.service;

import com.swiftdrop.notification.domain.entity.InAppNotification;
import com.swiftdrop.notification.domain.repository.InAppNotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InAppNotificationService {

    private final InAppNotificationRepository repository;

    @Transactional
    public InAppNotification saveNotification(UUID userId, String title, String body, String trackingNumber) {
        return saveNotification(userId, null, title, body, trackingNumber);
    }

    /**
     * Writes an inbox row of the given type (template code). The inbox is always written: the "push"
     * preference (push_enabled) switches lock-screen notifications on devices, not the inbox.
     * Returns null when nothing was stored.
     */
    @Transactional
    public InAppNotification saveNotification(UUID userId, String notificationType, String title, String body,
                                              String trackingNumber) {
        if (userId == null) {
            log.warn("Cannot save in-app notification without userId");
            return null;
        }
        InAppNotification notification = InAppNotification.builder()
                .userId(userId)
                .notificationType(notificationType)
                .title(title != null ? title : "SwiftDrop Update")
                .body(body != null ? body : "")
                .trackingNumber(trackingNumber)
                .read(false)
                .createdAt(Instant.now())
                .build();

        InAppNotification saved = repository.save(notification);
        log.info("Saved in-app notification id={} for user={}", saved.getId(), userId);
        return saved;
    }

    /**
     * Writes a team notification (for example to OPERATIONS or a merchant company). Team rows have no
     * per-user preference check and their read state is shared by the team.
     */
    @Transactional
    public InAppNotification saveTeamNotification(String audience, String notificationType, String title, String body,
                                                  String trackingNumber) {
        if (audience == null || audience.isBlank()) {
            return null;
        }
        InAppNotification saved = repository.save(InAppNotification.builder()
                .audience(audience)
                .notificationType(notificationType)
                .title(title != null ? title : "SwiftDrop Update")
                .body(body != null ? body : "")
                .trackingNumber(trackingNumber)
                .read(false)
                .createdAt(Instant.now())
                .build());
        log.info("Saved team notification id={} for audience={}", saved.getId(), audience);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<InAppNotification> listNotifications(UUID userId) {
        return listNotifications(userId, List.of());
    }

    /** The user's own notifications plus those of their team audiences, newest first. */
    @Transactional(readOnly = true)
    public List<InAppNotification> listNotifications(UUID userId, Collection<String> audiences) {
        if (userId == null) {
            return List.of();
        }
        return repository.findInbox(userId, nonEmpty(audiences));
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(UUID userId) {
        return getUnreadCount(userId, List.of());
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(UUID userId, Collection<String> audiences) {
        if (userId == null) {
            return 0;
        }
        return repository.countUnreadInInbox(userId, nonEmpty(audiences));
    }

    @Transactional
    public Optional<InAppNotification> markAsRead(Long id, UUID userId) {
        return markAsRead(id, userId, List.of());
    }

    /** Marks one notification read if it is in the caller's inbox (their own or one of their teams'). */
    @Transactional
    public Optional<InAppNotification> markAsRead(Long id, UUID userId, Collection<String> audiences) {
        if (id == null || userId == null) {
            return Optional.empty();
        }
        return repository.findInInbox(id, userId, nonEmpty(audiences))
                .map(n -> {
                    n.setRead(true);
                    return repository.save(n);
                });
    }

    @Transactional
    public int markAllAsRead(UUID userId) {
        return markAllAsRead(userId, List.of());
    }

    @Transactional
    public int markAllAsRead(UUID userId, Collection<String> audiences) {
        if (userId == null) {
            return 0;
        }
        return repository.markInboxRead(userId, nonEmpty(audiences));
    }

    /** JPQL "IN ()" with an empty list is invalid on some databases, so use a value no row can have. */
    private static Collection<String> nonEmpty(Collection<String> audiences) {
        return audiences == null || audiences.isEmpty() ? List.of("__none__") : audiences;
    }
}
