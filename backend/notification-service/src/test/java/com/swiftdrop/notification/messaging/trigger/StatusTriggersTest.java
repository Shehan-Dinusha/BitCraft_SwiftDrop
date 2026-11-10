package com.swiftdrop.notification.messaging.trigger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.notification.dto.DispatchNotificationRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StatusTriggersTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final PickedUpTrigger pickedUpTrigger = new PickedUpTrigger();
    private final OutForDeliveryTrigger outForDeliveryTrigger = new OutForDeliveryTrigger();
    private final DeliveredTrigger deliveredTrigger = new DeliveredTrigger();
    private final FailedTrigger failedTrigger = new FailedTrigger();

    private EventEnvelope createEnvelope(String routingKey, String json) {
        MessageProperties props = new MessageProperties();
        props.setReceivedRoutingKey(routingKey);
        Message message = new Message(json.getBytes(StandardCharsets.UTF_8), props);
        Optional<EventEnvelope> opt = EventEnvelope.from(message, objectMapper);
        assertThat(opt).isPresent();
        return opt.get();
    }

    @Test
    @DisplayName("PickedUpTrigger should match PICKED_UP and build SMS request")
    void testPickedUpTrigger() {
        UUID customerId = UUID.randomUUID();
        String json = """
                {
                    "customerId": "%s",
                    "trackingNumber": "SD-TRK-1",
                    "newPublicStatus": "PICKED_UP",
                    "courierName": "Amila"
                }
                """.formatted(customerId);

        EventEnvelope envelope = createEnvelope("parcel.status.changed.picked_up", json);

        assertThat(pickedUpTrigger.matches(envelope)).isTrue();
        assertThat(outForDeliveryTrigger.matches(envelope)).isFalse();
        assertThat(deliveredTrigger.matches(envelope)).isFalse();
        assertThat(failedTrigger.matches(envelope)).isFalse();

        List<DispatchNotificationRequest> requests = pickedUpTrigger.build(envelope);
        assertThat(requests).hasSize(1);
        DispatchNotificationRequest req = requests.getFirst();
        assertThat(req.recipientId()).isEqualTo(customerId);
        assertThat(req.channel()).isEqualTo("SMS");
        assertThat(req.templateCode()).isEqualTo("PARCEL_PICKED_UP");
        assertThat(req.templateParams()).containsEntry("courierName", "Amila");
    }

    @Test
    @DisplayName("OutForDeliveryTrigger should match OUT_FOR_DELIVERY and build SMS request")
    void testOutForDeliveryTrigger() {
        UUID customerId = UUID.randomUUID();
        String json = """
                {
                    "customerId": "%s",
                    "trackingNumber": "SD-TRK-2",
                    "newPublicStatus": "OUT_FOR_DELIVERY",
                    "courierName": "Nimal"
                }
                """.formatted(customerId);

        EventEnvelope envelope = createEnvelope("parcel.status.changed.out_for_delivery", json);

        assertThat(outForDeliveryTrigger.matches(envelope)).isTrue();
        assertThat(deliveredTrigger.matches(envelope)).isFalse();

        List<DispatchNotificationRequest> requests = outForDeliveryTrigger.build(envelope);
        assertThat(requests).hasSize(1);
        DispatchNotificationRequest req = requests.getFirst();
        assertThat(req.channel()).isEqualTo("SMS");
        assertThat(req.templateCode()).isEqualTo("PARCEL_OUT_FOR_DELIVERY");
        assertThat(req.templateParams()).containsEntry("courierName", "Nimal");
    }

    @Test
    @DisplayName("DeliveredTrigger should match DELIVERED and build EMAIL request")
    void testDeliveredTrigger() {
        UUID customerId = UUID.randomUUID();
        String json = """
                {
                    "customerId": "%s",
                    "trackingNumber": "SD-TRK-3",
                    "newPublicStatus": "DELIVERED",
                    "deliveredAt": "2026-11-10T08:00:00Z"
                }
                """.formatted(customerId);

        EventEnvelope envelope = createEnvelope("parcel.status.changed.delivered", json);

        assertThat(deliveredTrigger.matches(envelope)).isTrue();

        List<DispatchNotificationRequest> requests = deliveredTrigger.build(envelope);
        assertThat(requests).hasSize(1);
        DispatchNotificationRequest req = requests.getFirst();
        assertThat(req.channel()).isEqualTo("EMAIL");
        assertThat(req.templateCode()).isEqualTo("PARCEL_DELIVERED");
        assertThat(req.templateParams()).containsEntry("deliveredAt", "2026-11-10T08:00:00Z");
    }

    @Test
    @DisplayName("FailedTrigger should match FAILED and include failureReason")
    void testFailedTrigger() {
        UUID customerId = UUID.randomUUID();
        String json = """
                {
                    "customerId": "%s",
                    "trackingNumber": "SD-TRK-4",
                    "newPublicStatus": "FAILED",
                    "failureReason": "Premises locked"
                }
                """.formatted(customerId);

        EventEnvelope envelope = createEnvelope("parcel.status.changed.failed", json);

        assertThat(failedTrigger.matches(envelope)).isTrue();

        List<DispatchNotificationRequest> requests = failedTrigger.build(envelope);
        assertThat(requests).hasSize(1);
        DispatchNotificationRequest req = requests.getFirst();
        assertThat(req.channel()).isEqualTo("SMS");
        assertThat(req.templateCode()).isEqualTo("PARCEL_FAILED");
        assertThat(req.templateParams()).containsEntry("failureReason", "Premises locked");
    }
}
