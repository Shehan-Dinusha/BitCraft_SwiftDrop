package com.swiftdrop.notification.service;

import com.swiftdrop.notification.domain.entity.InAppNotification;
import com.swiftdrop.notification.domain.repository.InAppNotificationRepository;
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

    @Transactional
    public InAppNotification saveNotification(UUID userId, String title, String body, String trackingNumber) {
        if (userId == null) {
            log.warn("Cannot save in-app notification without userId");
            return null;
        }

        InAppNotification notification = InAppNotification.builder()
                .userId(userId)
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
