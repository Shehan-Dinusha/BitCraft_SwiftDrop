package com.swiftdrop.notification.service.push;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PushMessagesTest {

    private static final Map<String, String> PARAMS = Map.of(
            "trackingNumber", "SD-1234-5678",
            // Everything else in the params must stay out of the payload.
            "courierName", "Kamal Perera",
            "failureReason", "the recipient was not available",
            "pickupDistrict", "Colombo",
            "codAmount", "4500.00");

    @Test
    @DisplayName("Customers: picked up, out for delivery, delivered and failed open the tracking page")
    void customerMessages() {
        assertThat(PushMessages.forTemplate("PARCEL_PICKED_UP", PARAMS)).hasValueSatisfying(m -> {
            assertThat(m.title()).isEqualTo("Picked up");
            assertThat(m.body()).isEqualTo("SD-1234-5678 has been collected");
            assertThat(m.url()).isEqualTo("/customer/tracking/SD-1234-5678");
            assertThat(m.tag()).isEqualTo("SD-1234-5678");
            assertThat(m.urgency()).isEqualTo("normal");
        });
        assertThat(PushMessages.forTemplate("PARCEL_FAILED", PARAMS)).hasValueSatisfying(m ->
                assertThat(m.body()).isEqualTo("SD-1234-5678 could not be delivered"));
        assertThat(PushMessages.supports("PARCEL_DELIVERED")).isTrue();
        assertThat(PushMessages.supports("PARCEL_OUT_FOR_DELIVERY")).isTrue();
    }

    @Test
    @DisplayName("Couriers: urgent, short-lived, opens their deliveries")
    void courierMessage() {
        assertThat(PushMessages.forTemplate("COURIER_ASSIGNED", PARAMS)).hasValueSatisfying(m -> {
            assertThat(m.title()).isEqualTo("New delivery assigned");
            assertThat(m.body()).isEqualTo("SD-1234-5678 · open your deliveries");
            assertThat(m.url()).isEqualTo("/courier/deliveries");
            assertThat(m.urgency()).isEqualTo("high");
            assertThat(m.ttlSeconds()).isEqualTo(15 * 60);
        });
    }

    @Test
    @DisplayName("Payloads never carry names, reasons, places or amounts")
    void noPersonalData() {
        for (String type : new String[]{"PARCEL_PICKED_UP", "PARCEL_OUT_FOR_DELIVERY", "PARCEL_DELIVERED", "PARCEL_FAILED", "COURIER_ASSIGNED"}) {
            PushMessages.PushMessage m = PushMessages.forTemplate(type, PARAMS).orElseThrow();
            String all = m.title() + m.body() + m.url() + m.tag();
            assertThat(all).doesNotContain("Kamal", "recipient", "Colombo", "4500");
        }
    }

    @Test
    @DisplayName("Not pushed: order created, team alerts and every security message")
    void unsupportedTypes() {
        for (String type : new String[]{"PARCEL_CREATED", "MERCHANT_COURIER_ASSIGNED", "OPS_DELIVERY_FAILED",
                "EMAIL_VERIFICATION", "PASSWORD_RESET", "LOGIN_MFA", "LOGIN_SUCCESS", "LOGIN_FAILED", null}) {
            assertThat(PushMessages.supports(type)).as(String.valueOf(type)).isFalse();
            assertThat(PushMessages.forTemplate(type, PARAMS)).isEmpty();
        }
    }

    @Test
    @DisplayName("A tracking number that looks wrong is left out rather than sent")
    void oddTrackingNumber() {
        assertThat(PushMessages.forTemplate("PARCEL_DELIVERED", Map.of("trackingNumber", "<script>"))).hasValueSatisfying(m -> {
            assertThat(m.body()).isEqualTo("Open SwiftDrop for the latest update");
            assertThat(m.url()).isEqualTo("/customer/orders");
            assertThat(m.tag()).isNull();
        });
    }
}
