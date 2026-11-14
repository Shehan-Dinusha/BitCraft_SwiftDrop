package com.swiftdrop.notification.service;

import com.swiftdrop.notification.client.ContactClient;
import com.swiftdrop.notification.domain.entity.NotificationDispatchLog;
import com.swiftdrop.notification.domain.entity.RecipientPreference;
import com.swiftdrop.notification.domain.repository.NotificationDispatchLogRepository;
import com.swiftdrop.notification.domain.repository.RecipientPreferenceRepository;
import com.swiftdrop.notification.dto.DispatchNotificationRequest;
import com.swiftdrop.notification.dto.NotificationDispatchLogResponse;
import com.swiftdrop.notification.dto.RecipientContact;
import com.swiftdrop.notification.service.email.EmailDeliveryException;
import com.swiftdrop.notification.service.email.EmailHtmlRenderer;
import com.swiftdrop.notification.service.email.EmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalMatchers.and;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MultiChannelNotificationDispatcherTest {

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

    @Spy
    private EmailHtmlRenderer emailHtmlRenderer = new EmailHtmlRenderer("https://swiftdrop.lk");

    @InjectMocks
    private MultiChannelNotificationDispatcher dispatcher;

    private UUID recipientId;

    @BeforeEach
    void setUp() {
        recipientId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Should suppress notification when email is disabled in user preferences")
    void testDispatch_SuppressedByPreference() {
        RecipientPreference pref = RecipientPreference.builder()
                .recipientId(recipientId)
                .emailEnabled(false)
                .emailAddress("customer@example.com")
                .build();

        when(preferenceRepository.findByRecipientId(recipientId)).thenReturn(Optional.of(pref));
        when(templateRenderer.renderTemplate(eq("PARCEL_PICKED_UP"), any()))
                .thenReturn(new TemplateRendererService.RenderedNotification("Picked Up", "Your parcel is picked up", "EMAIL"));
        when(logRepository.save(any(NotificationDispatchLog.class))).thenAnswer(i -> i.getArgument(0));

        NotificationDispatchLogResponse response = dispatcher.dispatch(new DispatchNotificationRequest(
                recipientId, "EMAIL", "PARCEL_PICKED_UP", null, Map.of(), "SD-001"));

        assertThat(response.status()).isEqualTo("SUPPRESSED");
        assertThat(response.failureReason()).contains("disabled in user preferences");
        assertThat(response.sentAt()).isNull();
        verifyNoInteractions(emailSender);
    }

    @Test
    @DisplayName("Should fail dispatch when no email address can be resolved")
    void testDispatch_MissingDestination() {
        when(preferenceRepository.findByRecipientId(recipientId)).thenReturn(Optional.empty());
        when(contactClient.lookupContact(recipientId)).thenReturn(Optional.empty());
        when(templateRenderer.renderTemplate(eq("PARCEL_PICKED_UP"), any()))
                .thenReturn(new TemplateRendererService.RenderedNotification("Picked Up", "Your parcel is picked up", "EMAIL"));
        when(logRepository.save(any(NotificationDispatchLog.class))).thenAnswer(i -> i.getArgument(0));

        NotificationDispatchLogResponse response = dispatcher.dispatch(new DispatchNotificationRequest(
                recipientId, "EMAIL", "PARCEL_PICKED_UP", null, Map.of(), "SD-001"));

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.failureReason()).contains("No valid EMAIL destination address");
        verifyNoInteractions(emailSender);
    }

    @Test
    @DisplayName("Should resolve the email address via ContactClient when preferences have none")
    void testDispatch_ResolvesFromContactClient() {
        when(preferenceRepository.findByRecipientId(recipientId)).thenReturn(Optional.empty());
        when(contactClient.lookupContact(recipientId))
                .thenReturn(Optional.of(new RecipientContact("iam-user@example.com")));
        when(templateRenderer.renderTemplate(eq("PARCEL_DELIVERED"), any()))
                .thenReturn(new TemplateRendererService.RenderedNotification("Delivered", "Your parcel was delivered", "EMAIL"));
        when(logRepository.save(any(NotificationDispatchLog.class))).thenAnswer(i -> i.getArgument(0));

        NotificationDispatchLogResponse response = dispatcher.dispatch(new DispatchNotificationRequest(
                recipientId, "EMAIL", "PARCEL_DELIVERED", null, Map.of(), "SD-IAM-001"));

        assertThat(response.status()).isEqualTo("SENT");
        assertThat(response.channel()).isEqualTo("EMAIL");
        assertThat(response.destination()).isEqualTo("iam-user@example.com");
        verify(contactClient).lookupContact(recipientId);
        verify(emailSender).send(eq("iam-user@example.com"), eq("Delivered"), startsWith("Your parcel was delivered"), contains("<!DOCTYPE html>"));
    }

    @Test
    @DisplayName("Should fail dispatch when the email address is malformed")
    void testDispatch_InvalidEmailAddress() {
        RecipientPreference pref = RecipientPreference.builder()
                .recipientId(recipientId)
                .emailEnabled(true)
                .emailAddress("not-an-email")
                .build();

        when(preferenceRepository.findByRecipientId(recipientId)).thenReturn(Optional.of(pref));
        when(templateRenderer.renderTemplate(eq("PARCEL_PICKED_UP"), any()))
                .thenReturn(new TemplateRendererService.RenderedNotification("Picked Up", "Your parcel is picked up", "EMAIL"));
        when(logRepository.save(any(NotificationDispatchLog.class))).thenAnswer(i -> i.getArgument(0));

        NotificationDispatchLogResponse response = dispatcher.dispatch(new DispatchNotificationRequest(
                recipientId, "EMAIL", "PARCEL_PICKED_UP", null, Map.of(), "SD-001"));

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.failureReason()).contains("Invalid email destination format");
        verifyNoInteractions(emailSender);
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
        verify(emailSender).send(eq("customer@example.com"), eq("Delivered"), startsWith("Delivered at your door"),
                and(contains("Delivered at your door"), contains("https://swiftdrop.lk/track/SD-001")));
    }

    @Test
    @DisplayName("Should log FAILED with a safe reason when the email provider rejects the message")
    void testDispatchEMAIL_ProviderRejects() {
        RecipientPreference pref = RecipientPreference.builder()
                .recipientId(recipientId)
                .emailEnabled(true)
                .emailAddress("customer@example.com")
                .build();

        when(preferenceRepository.findByRecipientId(recipientId)).thenReturn(Optional.of(pref));
        when(templateRenderer.renderTemplate(eq("PARCEL_FAILED"), any()))
                .thenReturn(new TemplateRendererService.RenderedNotification("Delivery failed", "We could not deliver", "EMAIL"));
        when(logRepository.save(any(NotificationDispatchLog.class))).thenAnswer(i -> i.getArgument(0));
        doThrow(new EmailDeliveryException("Azure email rejected the message (HTTP 401)"))
                .when(emailSender).send(any(), any(), any(), any());

        NotificationDispatchLogResponse response = dispatcher.dispatch(new DispatchNotificationRequest(
                recipientId, "EMAIL", "PARCEL_FAILED", null, Map.of(), "SD-002"));

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.sentAt()).isNull();
        assertThat(response.failureReason())
                .isEqualTo("Azure email rejected the message (HTTP 401)")
                .doesNotContain("customer@example.com");
    }

    @Test
    @DisplayName("SMS is no longer a channel: it is logged as FAILED and nothing is sent")
    void testDispatch_SmsChannelRejected() {
        when(preferenceRepository.findByRecipientId(recipientId)).thenReturn(Optional.empty());
        when(templateRenderer.renderTemplate(eq("PARCEL_PICKED_UP"), any()))
                .thenReturn(new TemplateRendererService.RenderedNotification("Picked Up", "Picked up", "EMAIL"));
        when(logRepository.save(any(NotificationDispatchLog.class))).thenAnswer(i -> i.getArgument(0));

        NotificationDispatchLogResponse response = dispatcher.dispatch(new DispatchNotificationRequest(
                recipientId, "SMS", "PARCEL_PICKED_UP", "+94771234567", Map.of(), "SD-003"));

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.failureReason()).isEqualTo("Unsupported channel SMS; use EMAIL or PUSH");
        verifyNoInteractions(emailSender);
    }

    @Test
    @DisplayName("PUSH is recorded as sent without using the email provider")
    void testDispatch_PushDoesNotUseEmailProvider() {
        when(preferenceRepository.findByRecipientId(recipientId)).thenReturn(Optional.empty());
        when(templateRenderer.renderTemplate(eq("PARCEL_PICKED_UP"), any()))
                .thenReturn(new TemplateRendererService.RenderedNotification("Picked Up", "Picked up", "EMAIL"));
        when(logRepository.save(any(NotificationDispatchLog.class))).thenAnswer(i -> i.getArgument(0));

        NotificationDispatchLogResponse response = dispatcher.dispatch(new DispatchNotificationRequest(
                recipientId, "PUSH", "PARCEL_PICKED_UP", null, Map.of(), "SD-004"));

        assertThat(response.status()).isEqualTo("SENT");
        assertThat(response.destination()).isEqualTo(recipientId.toString());
        verifyNoInteractions(emailSender);
    }

    @Test
    @DisplayName("Should query logs by recipientId, channel, or status")
    void testListLogs() {
        NotificationDispatchLog log1 = NotificationDispatchLog.builder()
                .logId(UUID.randomUUID())
                .recipientId(recipientId)
                .channel("EMAIL")
                .status("SENT")
                .createdAt(Instant.now())
                .build();

        when(logRepository.findByRecipientIdAndChannelOrderByCreatedAtDesc(recipientId, "EMAIL"))
                .thenReturn(List.of(log1));

        List<NotificationDispatchLogResponse> logs = dispatcher.listLogs(recipientId, "EMAIL", null);

        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).channel()).isEqualTo("EMAIL");
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
