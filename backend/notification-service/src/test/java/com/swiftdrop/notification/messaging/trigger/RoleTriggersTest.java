package com.swiftdrop.notification.messaging.trigger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.notification.dto.DispatchNotificationRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Notifications for couriers, merchants, operations staff and sign-in security alerts. */
class RoleTriggersTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private EventEnvelope envelope(String routingKey, String json) {
        MessageProperties props = new MessageProperties();
        props.setReceivedRoutingKey(routingKey);
        return EventEnvelope.from(new Message(json.getBytes(StandardCharsets.UTF_8), props), objectMapper).orElseThrow();
    }

    @Test
    @DisplayName("Courier assignment emails the courier and tells the merchant company")
    void courierAssigned() {
        UUID courierId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        EventEnvelope env = envelope("dispatch.courier.assigned", """
                {"courierId":"%s","courierName":"Kamal Perera","trackingNumber":"SD-AAAA-BBBB","merchantId":"%s",
                 "pickupDistrict":"COLOMBO","deliveryDistrict":"NUWARA_ELIYA","estimatedPickupMinutes":18}
                """.formatted(courierId, merchantId));
        CourierAssignedTrigger trigger = new CourierAssignedTrigger();

        assertThat(trigger.matches(env)).isTrue();
        List<DispatchNotificationRequest> requests = trigger.build(env);
        assertThat(requests).singleElement().satisfies(r -> {
            assertThat(r.recipientId()).isEqualTo(courierId);
            assertThat(r.channel()).isEqualTo("EMAIL");
            assertThat(r.templateCode()).isEqualTo("COURIER_ASSIGNED");
            assertThat(r.templateParams()).containsEntry("pickupDistrict", "Colombo")
                    .containsEntry("deliveryDistrict", "Nuwara Eliya").containsEntry("estimatedPickupMinutes", "18");
        });

        assertThat(trigger.teamNotifications(env)).singleElement().satisfies(team -> {
            assertThat(team.audience()).isEqualTo("MERCHANT:" + merchantId);
            assertThat(team.notificationType()).isEqualTo("MERCHANT_COURIER_ASSIGNED");
            assertThat(team.body()).contains("Kamal Perera").contains("SD-AAAA-BBBB").contains("Colombo → Nuwara Eliya");
        });
    }

    @Test
    @DisplayName("Courier assignment without a merchant creates no team notification")
    void courierAssignedWithoutMerchant() {
        EventEnvelope env = envelope("dispatch.courier.assigned",
                "{\"courierId\":\"%s\",\"trackingNumber\":\"SD-AAAA-BBBB\"}".formatted(UUID.randomUUID()));

        assertThat(new CourierAssignedTrigger().teamNotifications(env)).isEmpty();
    }

    @Test
    @DisplayName("A failed dispatch alerts the operations team in-app only")
    void dispatchFailed() {
        EventEnvelope env = envelope("dispatch.failed", """
                {"orderId":"3fa85f64-5717-4562-b3fc-2c963f66afa6","failureReason":"No available courier in KANDY",
                 "attemptCount":3,"retryable":false}
                """);
        DispatchFailedTrigger trigger = new DispatchFailedTrigger();

        assertThat(trigger.matches(env)).isTrue();
        assertThat(trigger.build(env)).isEmpty();
        assertThat(trigger.teamNotifications(env)).singleElement().satisfies(team -> {
            assertThat(team.audience()).isEqualTo("OPERATIONS");
            assertThat(team.notificationType()).isEqualTo("DISPATCH_FAILED");
            assertThat(team.body()).contains("3FA85F64").contains("No available courier in KANDY")
                    .contains("Attempt 3").contains("assign a courier manually");
        });
    }

    @Test
    @DisplayName("A merchant order also appears in the merchant company's team inbox")
    void merchantOrderCreated() {
        UUID merchantId = UUID.randomUUID();
        EventEnvelope env = envelope("order.created", """
                {"customerId":"%s","merchantId":"%s","trackingNumber":"SD-CCCC-DDDD",
                 "pickupDistrict":"COLOMBO","deliveryDistrict":"KANDY","speed":"EXPRESS"}
                """.formatted(UUID.randomUUID(), merchantId));

        assertThat(new OrderCreatedTrigger().teamNotifications(env)).singleElement().satisfies(team -> {
            assertThat(team.audience()).isEqualTo("MERCHANT:" + merchantId);
            assertThat(team.notificationType()).isEqualTo("MERCHANT_ORDER_CREATED");
            assertThat(team.body()).isEqualTo("Order SD-CCCC-DDDD was created (Colombo → Kandy), Express delivery.");
        });
    }

    @Test
    @DisplayName("A successful sign-in emails a security alert with readable time, device and masked IP")
    void loginSuccess() {
        UUID userId = UUID.randomUUID();
        EventEnvelope env = envelope("notification.auth.login", """
                {"recipientId":"%s","outcome":"SUCCESS","ipAddress":"203.94.12.7","occurredAt":"2026-11-19T15:56:00Z",
                 "userAgent":"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0 Safari/537.36"}
                """.formatted(userId));
        AuthLoginTrigger trigger = new AuthLoginTrigger();

        assertThat(trigger.matches(env)).isTrue();
        assertThat(trigger.build(env)).singleElement().satisfies(r -> {
            assertThat(r.recipientId()).isEqualTo(userId);
            assertThat(r.templateCode()).isEqualTo("LOGIN_SUCCESS");
            assertThat(r.destination()).isNull();
            // 15:56 UTC is 9:26 p.m. in Sri Lanka.
            assertThat(r.templateParams()).containsEntry("occurredAt", "19 Nov 2026, 9:26 p.m.")
                    .containsEntry("device", "Chrome on Windows")
                    .containsEntry("ipAddress", "203.94.x.x");
        });
    }

    @Test
    @DisplayName("A failed sign-in uses the failed template; unknown outcomes are ignored")
    void loginFailedAndUnknown() {
        UUID userId = UUID.randomUUID();
        AuthLoginTrigger trigger = new AuthLoginTrigger();

        assertThat(trigger.build(envelope("notification.auth.login",
                "{\"recipientId\":\"%s\",\"outcome\":\"FAILED\"}".formatted(userId))))
                .singleElement().satisfies(r -> {
                    assertThat(r.templateCode()).isEqualTo("LOGIN_FAILED");
                    assertThat(r.templateParams()).containsEntry("device", "an unknown device").doesNotContainKey("ipAddress");
                });
        assertThat(trigger.build(envelope("notification.auth.login",
                "{\"recipientId\":\"%s\",\"outcome\":\"MAYBE\"}".formatted(userId)))).isEmpty();
    }
}
