package com.swiftdrop.notification.service;

import com.swiftdrop.notification.domain.entity.NotificationDispatchLog;
import com.swiftdrop.notification.domain.entity.RecipientPreference;
import com.swiftdrop.notification.domain.repository.NotificationDispatchLogRepository;
import com.swiftdrop.notification.domain.repository.RecipientPreferenceRepository;
import com.swiftdrop.notification.dto.DispatchNotificationRequest;
import com.swiftdrop.notification.dto.NotificationDispatchLogResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MultiChannelNotificationDispatcherTest {

    @Mock
    private RecipientPreferenceRepository preferenceRepository;

    @Mock
    private NotificationDispatchLogRepository logRepository;

    @Mock
    private TemplateRendererService templateRenderer;

    @InjectMocks
    private MultiChannelNotificationDispatcher dispatcher;

    private UUID recipientId;

    @BeforeEach
    void setUp() {
        recipientId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Should successfully dispatch SMS when enabled and phone valid")
    void testDispatchSMS_Success() {
        RecipientPreference pref = RecipientPreference.builder()
                .recipientId(recipientId)
                .smsEnabled(true)
                .phoneNumber("+94771234567")
                .build();

        when(preferenceRepository.findByRecipientId(recipientId)).thenReturn(Optional.of(pref));
        when(templateRenderer.renderTemplate(eq("PARCEL_PICKED_UP"), any()))
                .thenReturn(new TemplateRendererService.RenderedNotification("Picked Up", "Your parcel is picked up", "SMS"));
        when(logRepository.save(any(NotificationDispatchLog.class))).thenAnswer(i -> i.getArgument(0));

        DispatchNotificationRequest request = new DispatchNotificationRequest(
                recipientId,
                "SMS",
                "PARCEL_PICKED_UP",
                null,
                Map.of("trackingNumber", "SD-001"),
                "SD-001"
        );

        NotificationDispatchLogResponse response = dispatcher.dispatch(request);

        assertThat(response.status()).isEqualTo("SENT");
        assertThat(response.channel()).isEqualTo("SMS");
        assertThat(response.destination()).isEqualTo("+94771234567");
        assertThat(response.sentAt()).isNotNull();
        assertThat(response.failureReason()).isNull();

        ArgumentCaptor<NotificationDispatchLog> logCaptor = ArgumentCaptor.forClass(NotificationDispatchLog.class);
        verify(logRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getStatus()).isEqualTo("SENT");
    }

    @Test
    @DisplayName("Should suppress notification when channel is disabled in user preferences")
    void testDispatch_SuppressedByPreference() {
        RecipientPreference pref = RecipientPreference.builder()
                .recipientId(recipientId)
                .smsEnabled(false)
                .phoneNumber("+94771234567")
                .build();

        when(preferenceRepository.findByRecipientId(recipientId)).thenReturn(Optional.of(pref));
        when(templateRenderer.renderTemplate(eq("PARCEL_PICKED_UP"), any()))
                .thenReturn(new TemplateRendererService.RenderedNotification("Picked Up", "Your parcel is picked up", "SMS"));
        when(logRepository.save(any(NotificationDispatchLog.class))).thenAnswer(i -> i.getArgument(0));

        DispatchNotificationRequest request = new DispatchNotificationRequest(
                recipientId,
                "SMS",
                "PARCEL_PICKED_UP",
                null,
                Map.of(),
                "SD-001"
        );

        NotificationDispatchLogResponse response = dispatcher.dispatch(request);

        assertThat(response.status()).isEqualTo("SUPPRESSED");
        assertThat(response.failureReason()).contains("disabled in user preferences");
        assertThat(response.sentAt()).isNull();
    }

    @Test
    @DisplayName("Should fail dispatch when destination is missing")
    void testDispatch_MissingDestination() {
        when(preferenceRepository.findByRecipientId(recipientId)).thenReturn(Optional.empty());
        when(templateRenderer.renderTemplate(eq("PARCEL_PICKED_UP"), any()))
                .thenReturn(new TemplateRendererService.RenderedNotification("Picked Up", "Your parcel is picked up", "SMS"));
        when(logRepository.save(any(NotificationDispatchLog.class))).thenAnswer(i -> i.getArgument(0));

        DispatchNotificationRequest request = new DispatchNotificationRequest(
                recipientId,
                "SMS",
                "PARCEL_PICKED_UP",
                null, // No destination provided in request or preferences
                Map.of(),
                "SD-001"
        );

        NotificationDispatchLogResponse response = dispatcher.dispatch(request);

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.failureReason()).contains("No valid SMS destination address");
    }

    @Test
    @DisplayName("Should fail dispatch when SMS phone number format is invalid")
    void testDispatchSMS_InvalidPhoneNumber() {
        RecipientPreference pref = RecipientPreference.builder()
                .recipientId(recipientId)
                .smsEnabled(true)
                .phoneNumber("invalid-phone")
                .build();

        when(preferenceRepository.findByRecipientId(recipientId)).thenReturn(Optional.of(pref));
        when(templateRenderer.renderTemplate(eq("PARCEL_PICKED_UP"), any()))
                .thenReturn(new TemplateRendererService.RenderedNotification("Picked Up", "Your parcel is picked up", "SMS"));
        when(logRepository.save(any(NotificationDispatchLog.class))).thenAnswer(i -> i.getArgument(0));

        DispatchNotificationRequest request = new DispatchNotificationRequest(
                recipientId,
                "SMS",
                "PARCEL_PICKED_UP",
                null,
                Map.of(),
                "SD-001"
        );

        NotificationDispatchLogResponse response = dispatcher.dispatch(request);

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.failureReason()).contains("Invalid phone number format");
    }

    @Test
    @DisplayName("Should successfully dispatch EMAIL when valid")
    void testDispatchEMAIL_Success() {
        RecipientPreference pref = RecipientPreference.builder()
                .recipientId(recipientId)
                .emailEnabled(true)
                .emailAddress("customer@example.com")
                .build();

        when(preferenceRepository.findByRecipientId(recipientId)).thenReturn(Optional.of(pref));
        when(templateRenderer.renderTemplate(eq("PARCEL_DELIVERED"), any()))
                .thenReturn(new TemplateRendererService.RenderedNotification("Delivered", "Delivered at your door", "EMAIL"));
        when(logRepository.save(any(NotificationDispatchLog.class))).thenAnswer(i -> i.getArgument(0));

        DispatchNotificationRequest request = new DispatchNotificationRequest(
                recipientId,
                "EMAIL",
                "PARCEL_DELIVERED",
                null,
                Map.of(),
                "SD-001"
        );

        NotificationDispatchLogResponse response = dispatcher.dispatch(request);

        assertThat(response.status()).isEqualTo("SENT");
        assertThat(response.channel()).isEqualTo("EMAIL");
        assertThat(response.destination()).isEqualTo("customer@example.com");
    }

    @Test
    @DisplayName("Should query logs by recipientId, channel, or status")
    void testListLogs() {
        NotificationDispatchLog log1 = NotificationDispatchLog.builder()
                .logId(UUID.randomUUID())
                .recipientId(recipientId)
                .channel("SMS")
                .status("SENT")
                .createdAt(Instant.now())
                .build();

        when(logRepository.findByRecipientIdAndChannelOrderByCreatedAtDesc(recipientId, "SMS"))
                .thenReturn(List.of(log1));

        List<NotificationDispatchLogResponse> logs = dispatcher.listLogs(recipientId, "SMS", null);

        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).channel()).isEqualTo("SMS");
    }

    @Test
    @DisplayName("Should get log detail by log UUID")
    void testGetLog() {
        UUID logId = UUID.randomUUID();
        NotificationDispatchLog entity = NotificationDispatchLog.builder()
                .logId(logId)
                .recipientId(recipientId)
                .channel("EMAIL")
                .status("SENT")
                .build();

        when(logRepository.findByLogId(logId)).thenReturn(Optional.of(entity));

        Optional<NotificationDispatchLogResponse> result = dispatcher.getLog(logId);
        assertThat(result).isPresent();
        assertThat(result.get().logId()).isEqualTo(logId);
    }
}
