package com.swiftdrop.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.notification.client.ContactClient;
import com.swiftdrop.notification.domain.entity.NotificationDispatchLog;
import com.swiftdrop.notification.domain.entity.RecipientPreference;
import com.swiftdrop.notification.domain.repository.NotificationDispatchLogRepository;
import com.swiftdrop.notification.domain.repository.ProcessedEventRepository;
import com.swiftdrop.notification.domain.repository.RecipientPreferenceRepository;
import com.swiftdrop.notification.dto.DispatchNotificationRequest;
import com.swiftdrop.notification.dto.NotificationDispatchLogResponse;
import com.swiftdrop.notification.dto.RecipientContact;
import com.swiftdrop.notification.messaging.consumer.NotificationEventConsumer;
import com.swiftdrop.notification.messaging.trigger.AuthOtpTrigger;
import com.swiftdrop.notification.service.email.EmailHtmlRenderer;
import com.swiftdrop.notification.service.email.EmailSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * One-time codes must be emailed in full but never stored: not in the audit log, not in the inbox,
 * and not blocked by the user's "email off" preference.
 */
@ExtendWith(MockitoExtension.class)
class OtpEmailDispatchTest {

    private static final String CODE = "482913";

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
    private EmailHtmlRenderer emailHtmlRenderer = new EmailHtmlRenderer();

    @InjectMocks
    private MultiChannelNotificationDispatcher dispatcher;

    private DispatchNotificationRequest request(UUID recipient) {
        return new DispatchNotificationRequest(recipient, "EMAIL", "EMAIL_VERIFICATION", null,
                Map.of("code", CODE, "expiresInMinutes", "10"), null);
    }

    private void stubRendering(UUID recipient) {
        when(templateRenderer.renderTemplate(eq("EMAIL_VERIFICATION"), any()))
                .thenReturn(new TemplateRendererService.RenderedNotification("Your SwiftDrop verification code",
                        "Your SwiftDrop verification code is " + CODE + ". It expires in 10 minutes.", "EMAIL"));
        when(contactClient.lookupContact(recipient)).thenReturn(Optional.of(new RecipientContact("alice@example.com")));
        when(logRepository.save(any(NotificationDispatchLog.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("Sends the code by email even when the user turned email notifications off")
    void ignoresTheEmailOffPreference() {
        UUID recipient = UUID.randomUUID();
        when(preferenceRepository.findByRecipientId(recipient)).thenReturn(Optional.of(
                RecipientPreference.builder().recipientId(recipient).emailEnabled(false).build()));
        stubRendering(recipient);

        NotificationDispatchLogResponse response = dispatcher.dispatch(request(recipient));

        assertThat(response.status()).isEqualTo("SENT");
        ArgumentCaptor<String> plain = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(emailSender).send(eq("alice@example.com"), eq("Your SwiftDrop verification code"),
                plain.capture(), html.capture());
        assertThat(plain.getValue()).contains(CODE);
        assertThat(html.getValue()).contains(CODE).contains("Never share this code")
                .doesNotContain("Track your parcel");
    }

    @Test
    @DisplayName("Sends the code only to the registered IAM address, never to a preference or requested address")
    void sendsOnlyToTheRegisteredAddress() {
        UUID recipient = UUID.randomUUID();
        // An attacker who changed the victim's preference email, or asked for a custom destination,
        // must not receive the victim's password-reset code.
        when(preferenceRepository.findByRecipientId(recipient)).thenReturn(Optional.of(
                RecipientPreference.builder().recipientId(recipient).emailEnabled(true)
                        .emailAddress("attacker@example.com").build()));
        when(templateRenderer.renderTemplate(eq("PASSWORD_RESET"), any()))
                .thenReturn(new TemplateRendererService.RenderedNotification("Reset your SwiftDrop password",
                        "Your SwiftDrop password reset code is " + CODE + ".", "EMAIL"));
        when(contactClient.lookupContact(recipient)).thenReturn(Optional.of(new RecipientContact("alice@example.com")));
        when(logRepository.save(any(NotificationDispatchLog.class))).thenAnswer(i -> i.getArgument(0));

        NotificationDispatchLogResponse response = dispatcher.dispatch(new DispatchNotificationRequest(recipient,
                "EMAIL", "PASSWORD_RESET", "attacker2@example.com", Map.of("code", CODE, "expiresInMinutes", "10"), null));

        assertThat(response.destination()).isEqualTo("alice@example.com");
        verify(emailSender).send(eq("alice@example.com"), any(), any(), any());
        verify(emailSender, never()).send(eq("attacker@example.com"), any(), any(), any());
        verify(emailSender, never()).send(eq("attacker2@example.com"), any(), any(), any());
    }

    @Test
    @DisplayName("Without a registered IAM address the code is not sent anywhere")
    void failsWithoutRegisteredAddress() {
        UUID recipient = UUID.randomUUID();
        when(preferenceRepository.findByRecipientId(recipient)).thenReturn(Optional.of(
                RecipientPreference.builder().recipientId(recipient).emailAddress("attacker@example.com").build()));
        when(templateRenderer.renderTemplate(eq("EMAIL_VERIFICATION"), any()))
                .thenReturn(new TemplateRendererService.RenderedNotification("Your SwiftDrop verification code",
                        "Your SwiftDrop verification code is " + CODE + ".", "EMAIL"));
        when(contactClient.lookupContact(recipient)).thenReturn(Optional.empty());
        when(logRepository.save(any(NotificationDispatchLog.class))).thenAnswer(i -> i.getArgument(0));

        NotificationDispatchLogResponse response = dispatcher.dispatch(request(recipient));

        assertThat(response.status()).isEqualTo("FAILED");
        verify(emailSender, never()).send(any(), any(), any(), any());
    }

    private void stubLoginAlert(UUID recipient, String template) {
        when(preferenceRepository.findByRecipientId(recipient)).thenReturn(Optional.of(
                RecipientPreference.builder().recipientId(recipient).emailEnabled(false)
                        .emailAddress("attacker@example.com").build()));
        when(templateRenderer.renderTemplate(eq(template), any()))
                .thenReturn(new TemplateRendererService.RenderedNotification("New sign-in to your SwiftDrop account",
                        "Your SwiftDrop account was signed in to.", "EMAIL"));
        when(contactClient.lookupContact(recipient)).thenReturn(Optional.of(new RecipientContact("alice@example.com")));
        when(logRepository.save(any(NotificationDispatchLog.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("Sign-in alerts ignore the email-off preference, go only to the IAM address and are not redacted")
    void loginAlertIsSecurityMail() {
        UUID recipient = UUID.randomUUID();
        stubLoginAlert(recipient, "LOGIN_SUCCESS");

        NotificationDispatchLogResponse response = dispatcher.dispatch(new DispatchNotificationRequest(recipient,
                "EMAIL", "LOGIN_SUCCESS", null, Map.of("occurredAt", "19 Nov 2026, 9:26 p.m.", "device", "Chrome on Windows"), null));

        assertThat(response.status()).isEqualTo("SENT");
        assertThat(response.destination()).isEqualTo("alice@example.com");
        assertThat(response.content()).doesNotContain("redacted");
        verify(emailSender).send(eq("alice@example.com"), any(), any(), any());
    }

    @Test
    @DisplayName("Only one failed-sign-in alert is emailed per account every 15 minutes")
    void failedLoginAlertsAreThrottled() {
        UUID recipient = UUID.randomUUID();
        stubLoginAlert(recipient, "LOGIN_FAILED");
        when(logRepository.existsByRecipientIdAndTemplateCodeAndStatusAndCreatedAtAfter(
                eq(recipient), eq("LOGIN_FAILED"), eq("SENT"), any())).thenReturn(true);

        NotificationDispatchLogResponse response = dispatcher.dispatch(new DispatchNotificationRequest(recipient,
                "EMAIL", "LOGIN_FAILED", null, Map.of(), null));

        assertThat(response.status()).isEqualTo("SUPPRESSED");
        assertThat(response.failureReason()).contains("15 minutes");
        verify(emailSender, never()).send(any(), any(), any(), any());
    }

    @Test
    @DisplayName("A sign-in from a device and network used in the last 30 days sends no alert; a new one does")
    void successAlertOnlyForANewDevice() {
        UUID recipient = UUID.randomUUID();
        stubLoginAlert(recipient, "LOGIN_SUCCESS");
        String known = "Chrome on Windows · 203.94.x.x";
        when(logRepository.existsByRecipientIdAndTemplateCodeAndReferenceIdAndStatusNotAndCreatedAtAfter(
                eq(recipient), eq("LOGIN_SUCCESS"), any(), eq("FAILED"), any()))
                .thenAnswer(i -> known.equals(i.getArgument(2)));

        NotificationDispatchLogResponse again = dispatcher.dispatch(new DispatchNotificationRequest(recipient,
                "EMAIL", "LOGIN_SUCCESS", null, Map.of(), known));
        assertThat(again.status()).isEqualTo("SUPPRESSED");
        assertThat(again.failureReason()).contains("30 days");
        verify(emailSender, never()).send(any(), any(), any(), any());

        NotificationDispatchLogResponse fresh = dispatcher.dispatch(new DispatchNotificationRequest(recipient,
                "EMAIL", "LOGIN_SUCCESS", null, Map.of(), "Safari on iOS · 112.134.x.x"));
        assertThat(fresh.status()).isEqualTo("SENT");
        verify(emailSender).send(eq("alice@example.com"), any(), any(), any());
    }

    @Test
    @DisplayName("Stores a redacted body in the dispatch log, never the code")
    void redactsTheAuditLog() {
        UUID recipient = UUID.randomUUID();
        when(preferenceRepository.findByRecipientId(recipient)).thenReturn(Optional.empty());
        stubRendering(recipient);

        NotificationDispatchLogResponse response = dispatcher.dispatch(request(recipient));

        ArgumentCaptor<NotificationDispatchLog> saved = ArgumentCaptor.forClass(NotificationDispatchLog.class);
        verify(logRepository).save(saved.capture());
        assertThat(saved.getValue().getContent()).doesNotContain(CODE).contains("redacted");
        assertThat(response.content()).doesNotContain(CODE);
    }

    @Test
    @DisplayName("The event consumer emails the code but never writes it to the in-app inbox")
    void consumerSkipsTheInbox() throws Exception {
        UUID recipient = UUID.randomUUID();
        MultiChannelNotificationDispatcher mockDispatcher = mock(MultiChannelNotificationDispatcher.class);
        ProcessedEventRepository processed = mock(ProcessedEventRepository.class);
        InAppNotificationService inbox = mock(InAppNotificationService.class);
        NotificationEventConsumer consumer = new NotificationEventConsumer(
                new ObjectMapper(), mockDispatcher, List.of(new AuthOtpTrigger()), processed, inbox);
        when(mockDispatcher.dispatch(any())).thenReturn(new NotificationDispatchLogResponse(
                UUID.randomUUID(), recipient, "EMAIL", "EMAIL_VERIFICATION", "alice@example.com",
                "Your SwiftDrop verification code", "[redacted]", "SENT", null, null, null, null));

        MessageProperties props = new MessageProperties();
        props.setReceivedRoutingKey("notification.auth.otp");
        String json = """
                {"eventId":"%s","recipientId":"%s","purpose":"EMAIL_VERIFICATION","code":"%s","expiresInMinutes":10}
                """.formatted(UUID.randomUUID(), recipient, CODE);
        consumer.onNotificationEvent(new Message(json.getBytes(StandardCharsets.UTF_8), props));

        ArgumentCaptor<DispatchNotificationRequest> dispatched = ArgumentCaptor.forClass(DispatchNotificationRequest.class);
        verify(mockDispatcher).dispatch(dispatched.capture());
        assertThat(dispatched.getValue().templateCode()).isEqualTo("EMAIL_VERIFICATION");
        verify(inbox, never()).saveNotification(any(), any(), any(), any(), any());
        verify(processed).save(any());
    }
}
