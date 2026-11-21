package com.swiftdrop.notification.messaging.trigger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.security.CallerContext;
import com.swiftdrop.notification.dto.DispatchNotificationRequest;
import com.swiftdrop.notification.service.email.EmailHtmlRenderer;
import com.swiftdrop.notification.util.InboxAudience;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MerchantApplicationTriggerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MerchantApplicationTrigger trigger = new MerchantApplicationTrigger();
    private final UUID owner = UUID.randomUUID();

    private EventEnvelope envelope(String outcome, String reason) {
        String json = """
                {"eventId":"%s","merchantId":"%s","recipientId":"%s","businessName":"Kandy Spice",
                 "operatingDistrict":"NUWARA_ELIYA","outcome":"%s","reason":%s}
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), owner, outcome,
                reason == null ? "null" : "\"" + reason + "\"");
        MessageProperties props = new MessageProperties();
        props.setReceivedRoutingKey("notification.merchant.application");
        return EventEnvelope.from(new Message(json.getBytes(StandardCharsets.UTF_8), props), objectMapper).orElseThrow();
    }

    @Test
    @DisplayName("A new application alerts only that district's dispatcher, and emails nobody")
    void submittedAlertsTheDistrictDispatcher() {
        EventEnvelope envelope = envelope("SUBMITTED", null);

        assertThat(trigger.matches(envelope)).isTrue();
        assertThat(trigger.build(envelope)).isEmpty();
        List<TeamNotification> alerts = trigger.teamNotifications(envelope);
        assertThat(alerts).hasSize(1);
        assertThat(alerts.getFirst().audience()).isEqualTo("DISTRICT_OPS:NUWARA_ELIYA");
        assertThat(alerts.getFirst().body()).contains("Kandy Spice").contains("Nuwara Eliya");
    }

    @Test
    @DisplayName("Approval emails the owner")
    void approvedEmailsTheOwner() {
        List<DispatchNotificationRequest> emails = trigger.build(envelope("APPROVED", null));

        assertThat(emails).hasSize(1);
        assertThat(emails.getFirst().recipientId()).isEqualTo(owner);
        assertThat(emails.getFirst().templateCode()).isEqualTo("MERCHANT_APPROVED");
        assertThat(trigger.teamNotifications(envelope("APPROVED", null))).isEmpty();
    }

    @Test
    @DisplayName("Rejection emails the owner with the reason")
    void rejectedEmailsTheReason() {
        List<DispatchNotificationRequest> emails = trigger.build(envelope("REJECTED", "Registration number not found"));

        assertThat(emails.getFirst().templateCode()).isEqualTo("MERCHANT_REJECTED");
        assertThat(emails.getFirst().templateParams()).containsEntry("reason", "Registration number not found");
        String html = new EmailHtmlRenderer().renderHtml("MERCHANT_REJECTED", "s", "b", null, emails.getFirst().templateParams());
        assertThat(html).contains("Registration number not found").contains("Business account");
    }

    @Test
    @DisplayName("The district audience reaches that district's dispatcher, not other dispatchers or admins")
    void districtAudience() {
        assertThat(InboxAudience.forCaller(new CallerContext(UUID.randomUUID(), UserRole.DISPATCHER, null,
                SriLankanDistrict.NUWARA_ELIYA))).contains("DISTRICT_OPS:NUWARA_ELIYA");
        assertThat(InboxAudience.forCaller(new CallerContext(UUID.randomUUID(), UserRole.DISPATCHER, null,
                SriLankanDistrict.GALLE))).doesNotContain("DISTRICT_OPS:NUWARA_ELIYA");
        assertThat(InboxAudience.forCaller(new CallerContext(UUID.randomUUID(), UserRole.SYSTEM_ADMIN, null, null)))
                .containsExactly(InboxAudience.OPERATIONS);
    }
}
