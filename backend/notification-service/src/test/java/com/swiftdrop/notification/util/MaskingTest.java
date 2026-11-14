package com.swiftdrop.notification.util;

import com.swiftdrop.notification.client.ContactClient;
import com.swiftdrop.notification.domain.entity.NotificationDispatchLog;
import com.swiftdrop.notification.domain.entity.RecipientPreference;
import com.swiftdrop.notification.domain.repository.NotificationDispatchLogRepository;
import com.swiftdrop.notification.domain.repository.RecipientPreferenceRepository;
import com.swiftdrop.notification.dto.DispatchNotificationRequest;
import com.swiftdrop.notification.service.MultiChannelNotificationDispatcher;
import com.swiftdrop.notification.service.TemplateRendererService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.swiftdrop.notification.service.email.EmailSender;
import com.swiftdrop.notification.service.email.MockEmailSender;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class MaskingTest {

    @Nested
    @DisplayName("Phone Masking Rules")
    class PhoneMasking {

        @Test
        @DisplayName("Should mask local 10-digit Sri Lankan phone to 07X***5678 format")
        void testMaskLocalPhone() {
            assertThat(PiiMasker.maskPhone("0771235678")).isEqualTo("07X***5678");
            assertThat(PiiMasker.maskPhone("0712345678")).isEqualTo("07X***5678");
            assertThat(PiiMasker.maskPhone("0769991234")).isEqualTo("07X***1234");
        }

        @Test
        @DisplayName("Should mask E.164 +94 Sri Lankan phone to 07X***5678 format")
        void testMaskE164Phone() {
            assertThat(PiiMasker.maskPhone("+94771235678")).isEqualTo("07X***5678");
            assertThat(PiiMasker.maskPhone("+94712345678")).isEqualTo("07X***5678");
            assertThat(PiiMasker.maskPhone("94771235678")).isEqualTo("07X***5678");
        }

        @Test
        @DisplayName("Should strip whitespace and punctuation before masking phone")
        void testMaskFormattedPhone() {
            assertThat(PiiMasker.maskPhone("+94 77 123 5678")).isEqualTo("07X***5678");
            assertThat(PiiMasker.maskPhone("077-123-5678")).isEqualTo("07X***5678");
        }

        @Test
        @DisplayName("Should handle null, blank, and short invalid phone numbers safely")
        void testMaskEdgeCases() {
            assertThat(PiiMasker.maskPhone(null)).isEmpty();
            assertThat(PiiMasker.maskPhone("")).isEmpty();
            assertThat(PiiMasker.maskPhone("   ")).isEmpty();
            assertThat(PiiMasker.maskPhone("123")).isEqualTo("***");
        }
    }

    @Nested
    @DisplayName("Email Masking Rules")
    class EmailMasking {

        @Test
        @DisplayName("Should mask email preserving first character and domain (a***@gmail.com)")
        void testMaskStandardEmail() {
            assertThat(PiiMasker.maskEmail("alice@gmail.com")).isEqualTo("a***@gmail.com");
            assertThat(PiiMasker.maskEmail("john.doe@swiftdrop.example")).isEqualTo("j***@swiftdrop.example");
            assertThat(PiiMasker.maskEmail("buddhika@company.lk")).isEqualTo("b***@company.lk");
        }

        @Test
        @DisplayName("Should mask single letter username email")
        void testMaskSingleLetterUsername() {
            assertThat(PiiMasker.maskEmail("x@domain.com")).isEqualTo("x***@domain.com");
        }

        @Test
        @DisplayName("Should handle null, blank, and invalid email formats safely")
        void testMaskInvalidEmail() {
            assertThat(PiiMasker.maskEmail(null)).isEmpty();
            assertThat(PiiMasker.maskEmail("")).isEmpty();
            assertThat(PiiMasker.maskEmail("   ")).isEmpty();
            assertThat(PiiMasker.maskEmail("no-at-symbol")).isEqualTo("***");
            assertThat(PiiMasker.maskEmail("@nodomain")).isEqualTo("***");
        }
    }

    @Nested
    @DisplayName("Destination Masking Rules")
    class DestinationMasking {

        @Test
        @DisplayName("Should detect and mask phone destination")
        void testMaskPhoneDestination() {
            assertThat(PiiMasker.maskDestination("+94771235678")).isEqualTo("07X***5678");
            assertThat(PiiMasker.maskDestination("0771235678")).isEqualTo("07X***5678");
        }

        @Test
        @DisplayName("Should detect and mask email destination")
        void testMaskEmailDestination() {
            assertThat(PiiMasker.maskDestination("customer@example.com")).isEqualTo("c***@example.com");
        }

        @Test
        @DisplayName("Should preserve UNRESOLVED sentinel value")
        void testPreserveUnresolvedSentinel() {
            assertThat(PiiMasker.maskDestination("UNRESOLVED")).isEqualTo("UNRESOLVED");
            assertThat(PiiMasker.maskDestination("unresolved")).isEqualTo("unresolved");
        }

        @Test
        @DisplayName("Should handle null or empty destination")
        void testMaskNullOrBlank() {
            assertThat(PiiMasker.maskDestination(null)).isEmpty();
            assertThat(PiiMasker.maskDestination("")).isEmpty();
        }
    }

    @Nested
    @DisplayName("Dispatcher Log PII Leak Prevention")
    class DispatcherLogSanitization {

        @Mock
        private RecipientPreferenceRepository preferenceRepository;

        @Mock
        private NotificationDispatchLogRepository logRepository;

        @Mock
        private TemplateRendererService templateRenderer;

        @Mock
        private ContactClient contactClient;

        // Real mock provider, so its own log line is covered by the PII assertions below.
        @Spy
        private EmailSender emailSender = new MockEmailSender();

        @InjectMocks
        private MultiChannelNotificationDispatcher dispatcher;

        @Test
        @DisplayName("Should never log full phone number or email address during dispatch")
        void testDispatcherLogsNeverLeakPii(CapturedOutput output) {
            UUID recipientId = UUID.randomUUID();
            String rawPhone = "+94771234567";
            String rawEmail = "customer@example.com";

            when(preferenceRepository.findByRecipientId(recipientId)).thenReturn(Optional.empty());
            when(templateRenderer.renderTemplate(eq("PARCEL_PICKED_UP"), any()))
                    .thenReturn(new TemplateRendererService.RenderedNotification("Picked Up", "Your parcel is picked up", "EMAIL"));
            when(templateRenderer.renderTemplate(eq("PARCEL_DELIVERED"), any()))
                    .thenReturn(new TemplateRendererService.RenderedNotification("Delivered", "Your parcel was delivered", "EMAIL"));
            when(logRepository.save(any(NotificationDispatchLog.class))).thenAnswer(i -> i.getArgument(0));

            // A phone number mistakenly given as the email destination must still be masked in the error log
            DispatchNotificationRequest misaddressedRequest = new DispatchNotificationRequest(
                    recipientId, "EMAIL", "PARCEL_PICKED_UP", rawPhone, Map.of(), "REF-PHONE"
            );
            dispatcher.dispatch(misaddressedRequest);

            // Dispatch EMAIL
            DispatchNotificationRequest emailRequest = new DispatchNotificationRequest(
                    recipientId, "EMAIL", "PARCEL_DELIVERED", rawEmail, Map.of(), "REF-EMAIL"
            );
            dispatcher.dispatch(emailRequest);

            String logs = output.getAll();

            // Assert raw phone and email NEVER appear in the log output
            assertThat(logs)
                    .doesNotContain(rawPhone)
                    .doesNotContain("0771234567")
                    .doesNotContain(rawEmail);

            // Assert masked phone and email DO appear in the log output
            assertThat(logs)
                    .contains("07X***4567")
                    .contains("c***@example.com");
        }
    }
}
