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
    private EmailHtmlRenderer emailHtmlRenderer = new EmailHtmlRenderer("https://swiftdrop.lk");

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
        verify(inbox, never()).saveNotification(any(), any(), any(), any());
        verify(processed).save(any());
    }
}
