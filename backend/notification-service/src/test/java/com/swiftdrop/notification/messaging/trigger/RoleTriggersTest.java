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
    @DisplayName("A customer order that merely names a merchant does not notify that merchant")
    void courierAssignedForCustomerOrderNamingAMerchant() {
        EventEnvelope env = envelope("dispatch.courier.assigned", """
                {"courierId":"%s","customerId":"%s","merchantId":"%s","trackingNumber":"SD-AAAA-BBBB"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));

        assertThat(new CourierAssignedTrigger().teamNotifications(env)).isEmpty();
    }

    @Test
    @DisplayName("A dispatch failure that dispatch retries by itself alerts nobody")
    void retryableDispatchFailureIsSilent() {
        EventEnvelope env = envelope("dispatch.failed",
                "{\"orderId\":\"3fa85f64-5717-4562-b3fc-2c963f66afa6\",\"attemptCount\":1,\"retryable\":true}");

        assertThat(new DispatchFailedTrigger().teamNotifications(env)).isEmpty();
    }

    @Test
    @DisplayName("Merchants are not told about orders they created themselves")
    void merchantOrderCreatedHasNoTeamRow() {
        UUID merchantId = UUID.randomUUID();
        EventEnvelope env = envelope("order.created", """
                {"customerId":"%s","merchantId":"%s","trackingNumber":"SD-CCCC-DDDD"}
                """.formatted(merchantId, merchantId));

        assertThat(new OrderCreatedTrigger().teamNotifications(env)).isEmpty();
        assertThat(env.merchantOrderMerchantId()).contains(merchantId);
    }

    @Test
    @DisplayName("A failed delivery tells the customer, the operations team and the merchant, in plain words")
    void deliveryFailed() {
        UUID customerId = UUID.randomUUID();
        EventEnvelope env = envelope("parcel.status.changed", """
                {"customerId":"%s","trackingNumber":"SD-EEEE-FFFF","newPublicStatus":"FAILED",
                 "failureReason":"RECIPIENT_UNAVAILABLE"}
                """.formatted(customerId));
        FailedTrigger trigger = new FailedTrigger();

        assertThat(trigger.build(env)).singleElement().satisfies(r ->
                assertThat(r.templateParams()).containsEntry("failureReason", "the recipient was not available"));
        List<TeamNotification> rows = trigger.teamNotifications(env);
        assertThat(rows).extracting(TeamNotification::audience)
                .containsExactly("OPERATIONS", "MERCHANT:" + customerId); // the consumer keeps the merchant row only for a merchant company
        assertThat(rows.get(0).notificationType()).isEqualTo("OPS_DELIVERY_FAILED");
        assertThat(rows.get(0).body()).isEqualTo("Parcel SD-EEEE-FFFF could not be delivered: the recipient was not available.");
        assertThat(rows.get(1).notificationType()).isEqualTo("MERCHANT_DELIVERY_FAILED");
    }

    @Test
    @DisplayName("A failed attempt is told once and says what happens next; the hand-in at the hub is no news")
    void attemptFailed() {
        EventEnvelope attempt = statusEvent("OUT_FOR_DELIVERY", "DELIVERY_ATTEMPTED");
        FailedTrigger trigger = new FailedTrigger();

        assertThat(trigger.matches(attempt)).isTrue();
        assertThat(trigger.matches(statusEvent("DELIVERY_ATTEMPTED", "DELIVERY_ATTEMPTED"))).isFalse();
        assertThat(trigger.build(attempt)).singleElement().satisfies(r ->
                assertThat(r.templateParams().get("nextStep")).contains("try again or return it to you"));
        assertThat(trigger.teamNotifications(attempt).get(0).body())
                .endsWith("It comes back to the hub: try again or return it to the sender.");
    }

    @Test
    @DisplayName("A return is told when it starts and when the sender has the parcel back")
    void returned() {
        ReturnTrigger trigger = new ReturnTrigger();
        EventEnvelope starts = statusEvent("DELIVERY_ATTEMPTED", "RETURNING");
        EventEnvelope done = statusEvent("RETURNING", "RETURNED_TO_SENDER");

        assertThat(trigger.matches(starts)).isTrue();
        assertThat(trigger.matches(statusEvent("RETURNING", "RETURNING"))).isFalse();
        assertThat(trigger.matches(statusEvent("OUT_FOR_DELIVERY", "DELIVERED"))).isFalse();
        assertThat(trigger.build(starts).get(0).templateCode()).isEqualTo("PARCEL_RETURNING");
        assertThat(trigger.build(done).get(0).templateCode()).isEqualTo("PARCEL_RETURNED");
        assertThat(trigger.build(done).get(0).templateParams()).containsKey("returnedAt");
        assertThat(trigger.teamNotifications(starts).get(0).notificationType()).isEqualTo("MERCHANT_RETURN");
        assertThat(trigger.teamNotifications(done).get(0).title()).isEqualTo("Parcel returned");
    }

    private EventEnvelope statusEvent(String from, String to) {
        return envelope("parcel.status.changed", """
                {"customerId":"%s","trackingNumber":"SD-EEEE-FFFF","previousPublicStatus":"%s","newPublicStatus":"%s",
                 "failureReason":"RECIPIENT_UNAVAILABLE","timestamp":"2026-11-22T10:00:00Z"}
                """.formatted(UUID.randomUUID(), from, to));
    }

    @Test
    @DisplayName("Delivered shows a readable time and picked-up never invents a courier name")
    void readableStatusParams() {
        UUID customerId = UUID.randomUUID();
        EventEnvelope delivered = envelope("parcel.status.changed", """
                {"customerId":"%s","trackingNumber":"SD-EEEE-FFFF","newPublicStatus":"DELIVERED",
                 "timestamp":"2026-11-19T15:56:00Z"}
                """.formatted(customerId));
        assertThat(new DeliveredTrigger().build(delivered)).singleElement().satisfies(r ->
                assertThat(r.templateParams()).containsEntry("deliveredAt", "19 Nov 2026, 9:26 p.m."));

        EventEnvelope pickedUp = envelope("parcel.status.changed", """
                {"customerId":"%s","trackingNumber":"SD-EEEE-FFFF","newPublicStatus":"PICKED_UP"}
                """.formatted(customerId));
        assertThat(new PickedUpTrigger().build(pickedUp)).singleElement().satisfies(r ->
                assertThat(r.templateParams()).doesNotContainKey("courierName"));
    }

    @Test
    @DisplayName("No placeholder tracking number is made up when an event has none")
    void noPlaceholderTrackingNumber() {
        assertThat(envelope("dispatch.failed", "{\"orderId\":\"x\"}").extractTrackingNumber()).isNull();
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
            // The device key the dispatcher uses to alert only for a new device or network.
            assertThat(r.referenceId()).isEqualTo("Chrome on Windows · 203.94.x.x");
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
