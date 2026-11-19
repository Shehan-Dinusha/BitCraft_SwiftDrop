package com.swiftdrop.notification.service;

import com.swiftdrop.notification.domain.entity.InAppNotification;
import com.swiftdrop.notification.domain.repository.InAppNotificationRepository;
import com.swiftdrop.notification.domain.repository.RecipientPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InAppNotificationService {

    private final InAppNotificationRepository repository;
    private final RecipientPreferenceRepository preferenceRepository;

    @Transactional
    public InAppNotification saveNotification(UUID userId, String title, String body, String trackingNumber) {
        return saveNotification(userId, null, title, body, trackingNumber);
    }

    /**
     * Writes an inbox row of the given type (template code), unless the user switched in-app
     * notifications off in their preferences. Returns null when nothing was stored.
     */
    @Transactional
    public InAppNotification saveNotification(UUID userId, String notificationType, String title, String body,
                                              String trackingNumber) {
        if (userId == null) {
            log.warn("Cannot save in-app notification without userId");
            return null;
        }
        if (inAppDisabled(userId)) {
            log.debug("In-app notifications are off for user {}; not storing", userId);
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

    /** The "In-app notifications" preference (stored as push_enabled); defaults to on. */
    private boolean inAppDisabled(UUID userId) {
        if (preferenceRepository == null) {
            return false;
        }
        return preferenceRepository.findByRecipientId(userId)
                .map(pref -> !pref.isPushEnabled())
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public List<InAppNotification> listNotifications(UUID userId) {
        if (userId == null) {
            return List.of();
        }
        return repository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(UUID userId) {
        if (userId == null) {
            return 0;
        }
        return repository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public Optional<InAppNotification> markAsRead(Long id, UUID userId) {
        if (id == null || userId == null) {
            return Optional.empty();
        }
        return repository.findByIdAndUserId(id, userId)
                .map(n -> {
                    n.setRead(true);
                    return repository.save(n);
                });
    }

    @Transactional
    public int markAllAsRead(UUID userId) {
        if (userId == null) {
            return 0;
        }
        return repository.markAllReadByUserId(userId);
    }
}

