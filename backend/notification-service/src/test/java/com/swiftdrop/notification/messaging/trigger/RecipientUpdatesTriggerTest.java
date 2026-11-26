package com.swiftdrop.notification.messaging.trigger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.notification.client.ContactClient;
import com.swiftdrop.notification.client.OrderRecipientClient;
import com.swiftdrop.notification.dto.DispatchNotificationRequest;
import com.swiftdrop.notification.dto.RecipientContact;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecipientUpdatesTriggerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OrderRecipientClient orders = mock(OrderRecipientClient.class);
    private final ContactClient accounts = mock(ContactClient.class);
    private final RecipientUpdatesTrigger trigger = new RecipientUpdatesTrigger(orders, accounts);
    private final PickupAssignedTrigger pickupAssigned = new PickupAssignedTrigger();

    private final UUID orderId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(orders.lookup(orderId)).thenReturn(Optional.of(new OrderRecipientClient.RecipientContact(
                "SD-7K4M-9QX2", "kamal@example.com", "Kamal Silva", "Nimal Perera", "2026-11-28")));
        when(accounts.lookupContact(customerId)).thenReturn(Optional.of(new RecipientContact("nimal@example.com")));
    }

    private EventEnvelope envelope(String routingKey, String json) {
        MessageProperties props = new MessageProperties();
        props.setReceivedRoutingKey(routingKey);
        return EventEnvelope.from(new Message(json.getBytes(StandardCharsets.UTF_8), props), objectMapper).orElseThrow();
    }

    private EventEnvelope status(String previous, String next, String failureReason) {
        return envelope("parcel.status.changed", """
                {"orderId":"%s","customerId":"%s","trackingNumber":"SD-7K4M-9QX2",
                 "previousPublicStatus":"%s","newPublicStatus":"%s","failureReason":%s}
                """.formatted(orderId, customerId, previous, next,
                failureReason == null ? "null" : "\"" + failureReason + "\""));
    }

    @Test
    @DisplayName("A new booking is emailed to the recipient's own address, with who sent it and the promised day")
    void bookingEmailsTheRecipient() {
        EventEnvelope created = envelope("order.created", """
                {"orderId":"%s","customerId":"%s","trackingNumber":"SD-7K4M-9QX2",
                 "pickupDistrict":"COLOMBO","deliveryDistrict":"KANDY","speed":"EXPRESS"}
                """.formatted(orderId, customerId));

        List<DispatchNotificationRequest> requests = trigger.build(created);

        assertThat(trigger.matches(created)).isTrue();
        assertThat(requests).singleElement().satisfies(request -> {
            assertThat(request.templateCode()).isEqualTo(RecipientUpdatesTrigger.CREATED);
            assertThat(request.destination()).isEqualTo("kamal@example.com");
            assertThat(request.recipientId()).isNotEqualTo(customerId).isNotNull();
            assertThat(request.templateParams())
                    .containsEntry("senderName", "Nimal Perera")
                    .containsEntry("deliverBy", "Saturday, 28 Nov")
                    .containsEntry("speed", "EXPRESS");
        });
    }

    @Test
    @DisplayName("Each change of public status the recipient cares about has its own email")
    void statusChangesMapToTemplates() {
        assertThat(trigger.build(status("CREATED", "PICKED_UP", null)))
                .extracting(DispatchNotificationRequest::templateCode).containsExactly(RecipientUpdatesTrigger.PICKED_UP);
        assertThat(trigger.build(status("IN_TRANSIT", "OUT_FOR_DELIVERY", null)))
                .extracting(DispatchNotificationRequest::templateCode).containsExactly(RecipientUpdatesTrigger.OUT_FOR_DELIVERY);
        assertThat(trigger.build(status("OUT_FOR_DELIVERY", "DELIVERED", null)))
                .extracting(DispatchNotificationRequest::templateCode).containsExactly(RecipientUpdatesTrigger.DELIVERED);
        assertThat(trigger.build(status("DELIVERY_ATTEMPTED", "RETURNING", null)))
                .extracting(DispatchNotificationRequest::templateCode).containsExactly(RecipientUpdatesTrigger.RETURNING);
    }

    @Test
    @DisplayName("Hub steps, repeats and the sender's own steps send the recipient nothing")
    void quietSteps() {
        assertThat(trigger.matches(status("PICKED_UP", "IN_TRANSIT", null))).isFalse();
        assertThat(trigger.matches(status("OUT_FOR_DELIVERY", "OUT_FOR_DELIVERY", null))).isFalse();
        assertThat(trigger.matches(status("CREATED", "PICKUP_ASSIGNED", null))).isFalse();
        assertThat(trigger.matches(status("RETURNING", "RETURNED_TO_SENDER", null))).isFalse();
    }

    @Test
    @DisplayName("A failed attempt says what happens next; a refusal goes back to the sender")
    void failedAttemptNextStep() {
        DispatchNotificationRequest away = trigger.build(status("OUT_FOR_DELIVERY", "DELIVERY_ATTEMPTED", "RECIPIENT_UNAVAILABLE")).getFirst();
        DispatchNotificationRequest refused = trigger.build(status("OUT_FOR_DELIVERY", "DELIVERY_ATTEMPTED", "RECIPIENT_REJECTED")).getFirst();

        assertThat(away.templateCode()).isEqualTo(RecipientUpdatesTrigger.FAILED);
        assertThat(away.templateParams().get("nextStep")).startsWith("We will try again");
        assertThat(refused.templateParams()).containsEntry("nextStep", "It goes back to the sender.");
    }

    @Test
    @DisplayName("A cancelled order tells the recipient it will not come")
    void cancellation() {
        EventEnvelope cancelled = envelope("order.cancelled", """
                {"orderId":"%s","customerId":"%s","reason":"Changed my mind"}
                """.formatted(orderId, customerId));

        assertThat(trigger.build(cancelled)).singleElement().satisfies(request -> {
            assertThat(request.templateCode()).isEqualTo(RecipientUpdatesTrigger.CANCELLED);
            assertThat(request.referenceId()).isEqualTo("SD-7K4M-9QX2");
        });
    }

    @Test
    @DisplayName("No email without a recipient address, or when it is the sender's own")
    void skipsMissingOrOwnAddress() {
        when(orders.lookup(orderId)).thenReturn(Optional.of(new OrderRecipientClient.RecipientContact(
                "SD-7K4M-9QX2", null, "Kamal Silva", "Nimal Perera", null)));
        assertThat(trigger.build(status("CREATED", "PICKED_UP", null))).isEmpty();

        when(orders.lookup(orderId)).thenReturn(Optional.of(new OrderRecipientClient.RecipientContact(
                "SD-7K4M-9QX2", "Nimal@Example.com", "Nimal Perera", "Nimal Perera", null)));
        assertThat(trigger.build(status("CREATED", "PICKED_UP", null))).isEmpty();
    }

    @Test
    @DisplayName("Events the recipient does not hear about never call the order service")
    void noLookupWhenNotNeeded() {
        EventEnvelope assigned = envelope("dispatch.courier.assigned", """
                {"orderId":"%s","courierId":"%s"}
                """.formatted(orderId, UUID.randomUUID()));

        assertThat(trigger.matches(assigned)).isFalse();
        verify(orders, never()).lookup(any());
    }

    @Test
    @DisplayName("The sender hears once that a courier is coming; a reassignment sends nothing")
    void senderHearsAboutThePickupCourierOnce() {
        EventEnvelope first = status("CREATED", "PICKUP_ASSIGNED", null);
        EventEnvelope reassigned = status("PICKUP_ASSIGNED", "PICKUP_ASSIGNED", null);

        assertThat(pickupAssigned.matches(first)).isTrue();
        assertThat(pickupAssigned.matches(reassigned)).isFalse();
        assertThat(pickupAssigned.build(first)).singleElement().satisfies(request -> {
            assertThat(request.recipientId()).isEqualTo(customerId);
            assertThat(request.destination()).isNull();
            assertThat(request.templateCode()).isEqualTo(PickupAssignedTrigger.TEMPLATE_CODE);
        });
    }
}
