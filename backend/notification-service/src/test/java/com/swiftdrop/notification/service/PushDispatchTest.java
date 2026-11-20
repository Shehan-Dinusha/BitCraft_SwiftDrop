package com.swiftdrop.notification.service;

import com.swiftdrop.notification.client.ContactClient;
import com.swiftdrop.notification.domain.entity.NotificationDispatchLog;
import com.swiftdrop.notification.domain.entity.RecipientPreference;
import com.swiftdrop.notification.domain.repository.NotificationDispatchLogRepository;
import com.swiftdrop.notification.domain.repository.RecipientPreferenceRepository;
import com.swiftdrop.notification.dto.DispatchNotificationRequest;
import com.swiftdrop.notification.dto.NotificationDispatchLogResponse;
import com.swiftdrop.notification.service.email.EmailHtmlRenderer;
import com.swiftdrop.notification.service.email.EmailSender;
import com.swiftdrop.notification.service.push.DevicePushService;
import com.swiftdrop.notification.service.push.PushMessages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The dispatcher's PUSH channel: device notifications through Web Push. */
@ExtendWith(MockitoExtension.class)
class PushDispatchTest {

    @Mock
    private RecipientPreferenceRepository preferenceRepository;
    @Mock
    private NotificationDispatchLogRepository logRepository;
    @Mock
    private TemplateRendererService templateRenderer;
    @Mock
    private ContactClient contactClient;
    @Mock
    private EmailSender emailSender;
    @Mock
    private DevicePushService devicePushService;

    private MultiChannelNotificationDispatcher dispatcher;
    private final UUID user = UUID.randomUUID();
    private final Map<String, String> params = Map.of("trackingNumber", "SD-1234-5678");

    @BeforeEach
    void setUp() {
        dispatcher = new MultiChannelNotificationDispatcher(preferenceRepository, logRepository, templateRenderer,
                contactClient, emailSender, new EmailHtmlRenderer(), devicePushService);
        lenient().when(preferenceRepository.findByRecipientId(user)).thenReturn(Optional.empty());
        lenient().when(templateRenderer.renderTemplate(any(), anyMap()))
                .thenReturn(new TemplateRendererService.RenderedNotification("Subject", "Body", "EMAIL"));
        lenient().when(logRepository.save(any(NotificationDispatchLog.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(devicePushService.enabled()).thenReturn(true);
    }

    private NotificationDispatchLogResponse push(String template) {
        return dispatcher.dispatch(new DispatchNotificationRequest(user, "PUSH", template, null, params, "SD-1234-5678"));
    }

    @Test
    @DisplayName("Sends the device message and logs it as SENT")
    void sendsToDevices() {
        when(devicePushService.push(eq(user), any())).thenReturn(new DevicePushService.PushResult(2, 2, 0, 0));

        NotificationDispatchLogResponse log = push("PARCEL_DELIVERED");

        assertThat(log.status()).isEqualTo("SENT");
        assertThat(log.channel()).isEqualTo("PUSH");
        assertThat(log.destination()).isEqualTo(user.toString()); // never the device endpoint
        ArgumentCaptor<PushMessages.PushMessage> message = ArgumentCaptor.forClass(PushMessages.PushMessage.class);
        verify(devicePushService).push(eq(user), message.capture());
        assertThat(message.getValue().url()).isEqualTo("/customer/tracking/SD-1234-5678");
        verifyNoInteractions(emailSender);
    }

    @ParameterizedTest
    @ValueSource(strings = {"EMAIL_VERIFICATION", "PASSWORD_RESET", "LOGIN_MFA", "LOGIN_SUCCESS", "LOGIN_FAILED"})
    @DisplayName("Codes and sign-in alerts are never pushed to a lock screen")
    void securityMessagesAreNeverPushed(String template) {
        NotificationDispatchLogResponse log = push(template);

        assertThat(log.status()).isEqualTo("SUPPRESSED");
        assertThat(log.failureReason()).contains("never sent to devices");
        verify(devicePushService, never()).push(any(), any());
    }

    @Test
    @DisplayName("Push turned off in the preferences: nothing is sent")
    void respectsThePushPreference() {
        when(preferenceRepository.findByRecipientId(user)).thenReturn(Optional.of(
                RecipientPreference.builder().recipientId(user).pushEnabled(false).build()));

        assertThat(push("PARCEL_DELIVERED").status()).isEqualTo("SUPPRESSED");
        verify(devicePushService, never()).push(any(), any());
    }

    @Test
    @DisplayName("When every device fails the log says FAILED, and nothing is thrown to the caller")
    void failureIsLoggedNotThrown() {
        when(devicePushService.push(eq(user), any())).thenReturn(new DevicePushService.PushResult(1, 0, 0, 1));

        assertThat(push("COURIER_ASSIGNED").status()).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("Expired devices removed is not a failure")
    void removedDevicesAreNotAFailure() {
        when(devicePushService.push(eq(user), any())).thenReturn(new DevicePushService.PushResult(1, 0, 1, 0));

        assertThat(push("PARCEL_PICKED_UP").status()).isEqualTo("SENT");
    }

    @Test
    @DisplayName("Without VAPID keys the PUSH channel stays the in-app inbox only")
    void disabledSendsNothing() {
        when(devicePushService.enabled()).thenReturn(false);

        assertThat(push("PARCEL_DELIVERED").status()).isEqualTo("SENT");
        verify(devicePushService, never()).push(any(), any());
    }
}
