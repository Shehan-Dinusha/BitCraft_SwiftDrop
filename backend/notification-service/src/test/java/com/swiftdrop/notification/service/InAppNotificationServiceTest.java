package com.swiftdrop.notification.service;

import com.swiftdrop.notification.domain.entity.InAppNotification;
import com.swiftdrop.notification.domain.repository.InAppNotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InAppNotificationServiceTest {

    @Mock
    private InAppNotificationRepository repository;

    @InjectMocks
    private InAppNotificationService service;

    @Test
    @DisplayName("Should save in-app notification with default unread state")
    void testSaveNotification() {
        UUID userId = UUID.randomUUID();
        when(repository.save(any(InAppNotification.class))).thenAnswer(i -> {
            InAppNotification entity = i.getArgument(0);
            entity.setId(100L);
            return entity;
        });

        InAppNotification saved = service.saveNotification(
                userId,
                "Parcel Picked Up",
                "Courier has picked up your parcel SD-001",
                "SD-001"
        );

        assertThat(saved).isNotNull();
        assertThat(saved.getId()).isEqualTo(100L);
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getTitle()).isEqualTo("Parcel Picked Up");
        assertThat(saved.getTrackingNumber()).isEqualTo("SD-001");
        assertThat(saved.isRead()).isFalse();
        verify(repository).save(any(InAppNotification.class));
    }

    @Test
    @DisplayName("Should list notifications for user ordered by created_at desc")
    void testListNotifications() {
        UUID userId = UUID.randomUUID();
        InAppNotification n1 = InAppNotification.builder().id(1L).userId(userId).title("Alert 1").build();
        InAppNotification n2 = InAppNotification.builder().id(2L).userId(userId).title("Alert 2").build();

        when(repository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(n2, n1));

        List<InAppNotification> list = service.listNotifications(userId);

        assertThat(list).hasSize(2).containsExactly(n2, n1);
        verify(repository).findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Test
    @DisplayName("Should mark single notification as read when owned by user")
    void testMarkAsRead_Success() {
        UUID userId = UUID.randomUUID();
        Long notificationId = 1L;
        InAppNotification existing = InAppNotification.builder()
                .id(notificationId)
                .userId(userId)
                .title("Alert")
                .read(false)
                .build();

        when(repository.findByIdAndUserId(notificationId, userId)).thenReturn(Optional.of(existing));
        when(repository.save(any(InAppNotification.class))).thenAnswer(i -> i.getArgument(0));

        Optional<InAppNotification> updated = service.markAsRead(notificationId, userId);

        assertThat(updated).isPresent();
        assertThat(updated.get().isRead()).isTrue();
        verify(repository).save(existing);
    }

    @Test
    @DisplayName("Should return empty when marking as read for non-existent notification or another user")
    void testMarkAsRead_NotFound() {
        UUID userId = UUID.randomUUID();
        Long notificationId = 999L;
        when(repository.findByIdAndUserId(notificationId, userId)).thenReturn(Optional.empty());

        Optional<InAppNotification> result = service.markAsRead(notificationId, userId);

        assertThat(result).isEmpty();
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Should mark all unread notifications as read for a user")
    void testMarkAllAsRead() {
        UUID userId = UUID.randomUUID();
        when(repository.markAllReadByUserId(userId)).thenReturn(5);

        int count = service.markAllAsRead(userId);

        assertThat(count).isEqualTo(5);
        verify(repository).markAllReadByUserId(userId);
    }

    @Test
    @DisplayName("Should return accurate unread count for user")
    void testGetUnreadCount() {
        UUID userId = UUID.randomUUID();
        when(repository.countByUserIdAndReadFalse(userId)).thenReturn(3L);

        long unread = service.getUnreadCount(userId);

        assertThat(unread).isEqualTo(3L);
        verify(repository).countByUserIdAndReadFalse(userId);
    }
}
