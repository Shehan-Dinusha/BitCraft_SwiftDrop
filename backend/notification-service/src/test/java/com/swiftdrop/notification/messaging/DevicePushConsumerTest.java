package com.swiftdrop.notification.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.notification.domain.repository.ProcessedEventRepository;
import com.swiftdrop.notification.dto.DispatchNotificationRequest;
import com.swiftdrop.notification.messaging.consumer.NotificationEventConsumer;
import com.swiftdrop.notification.messaging.trigger.AuthLoginTrigger;
import com.swiftdrop.notification.messaging.trigger.CourierAssignedTrigger;
import com.swiftdrop.notification.messaging.trigger.DeliveredTrigger;
import com.swiftdrop.notification.messaging.trigger.NotificationTrigger;
import com.swiftdrop.notification.messaging.trigger.OrderCreatedTrigger;
import com.swiftdrop.notification.service.InAppNotificationService;
import com.swiftdrop.notification.service.MultiChannelNotificationDispatcher;
import com.swiftdrop.notification.service.push.DevicePushService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** How the event consumer hands notifications to the device (Web Push) channel. */
@ExtendWith(MockitoExtension.class)
class DevicePushConsumerTest {

    @Mock
    private MultiChannelNotificationDispatcher dispatcher;
    @Mock
    private ProcessedEventRepository processedEventRepository;
    @Mock
    private InAppNotificationService inAppNotificationService;
    @Mock
    private DevicePushService devicePushService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private NotificationEventConsumer consumer(NotificationTrigger trigger) {
        return new NotificationEventConsumer(objectMapper, dispatcher, List.of(trigger), processedEventRepository,
                inAppNotificationService, null, devicePushService);
    }

    private static Message message(String routingKey, String json) {
        MessageProperties props = new MessageProperties();
        props.setReceivedRoutingKey(routingKey);
        return new Message(json.getBytes(StandardCharsets.UTF_8), props);
    }

    private static DispatchNotificationRequest channel(String channel) {
        return argThat(r -> r != null && channel.equals(r.channel()));
    }

    @Test
    @DisplayName("Delivered: email, then the inbox row, then the device push")
    void customerUpdateIsPushedAfterTheInbox() {
        UUID customer = UUID.randomUUID();
        when(devicePushService.hasDevices(customer)).thenReturn(true);

        consumer(new DeliveredTrigger()).onNotificationEvent(message("parcel.status.changed",
                "{\"customerId\":\"%s\",\"trackingNumber\":\"SD-1234-5678\",\"newPublicStatus\":\"DELIVERED\"}".formatted(customer)));

        InOrder order = inOrder(dispatcher, inAppNotificationService);
        order.verify(dispatcher).dispatch(channel("EMAIL"));
        order.verify(inAppNotificationService).saveNotification(eq(customer), eq("PARCEL_DELIVERED"), any(), any(), any());
        ArgumentCaptor<DispatchNotificationRequest> push = ArgumentCaptor.forClass(DispatchNotificationRequest.class);
        order.verify(dispatcher).dispatch(push.capture());
        assertThat(push.getValue().channel()).isEqualTo("PUSH");
        assertThat(push.getValue().templateCode()).isEqualTo("PARCEL_DELIVERED");
        assertThat(push.getValue().templateParams()).containsEntry("trackingNumber", "SD-1234-5678");
    }

    @Test
    @DisplayName("A failing push never undoes the inbox row or the email, and the event still completes")
    void pushFailureIsContained() {
        UUID courier = UUID.randomUUID();
        when(devicePushService.hasDevices(courier)).thenReturn(true);
        // Lenient: the EMAIL dispatch reaches the same method with other arguments.
        lenient().when(dispatcher.dispatch(channel("PUSH"))).thenThrow(new IllegalStateException("push service down"));

        assertThatCode(() -> consumer(new CourierAssignedTrigger()).onNotificationEvent(message("dispatch.courier.assigned",
                "{\"eventId\":\"%s\",\"courierId\":\"%s\",\"trackingNumber\":\"SD-1234-5678\"}".formatted(UUID.randomUUID(), courier))))
                .doesNotThrowAnyException();

        verify(dispatcher).dispatch(channel("EMAIL"));
        verify(inAppNotificationService).saveNotification(eq(courier), eq("COURIER_ASSIGNED"), any(), any(), any());
        verify(processedEventRepository).save(any());
    }

    @Test
    @DisplayName("Order created is not pushed: the customer just placed it")
    void orderCreatedIsNotPushed() {
        UUID customer = UUID.randomUUID();

        consumer(new OrderCreatedTrigger()).onNotificationEvent(message("order.created",
                "{\"customerId\":\"%s\",\"trackingNumber\":\"SD-1\"}".formatted(customer)));

        verify(dispatcher, never()).dispatch(channel("PUSH"));
        verify(devicePushService, never()).hasDevices(any());
    }

    @Test
    @DisplayName("Sign-in alerts never reach a device")
    void securityAlertsAreNotPushed() {
        UUID user = UUID.randomUUID();
        lenient().when(devicePushService.hasDevices(user)).thenReturn(true);

        consumer(new AuthLoginTrigger()).onNotificationEvent(message("notification.auth.login",
                "{\"recipientId\":\"%s\",\"outcome\":\"FAILED\"}".formatted(user)));

        verify(dispatcher, times(1)).dispatch(channel("EMAIL"));
        verify(dispatcher, never()).dispatch(channel("PUSH"));
    }

    @Test
    @DisplayName("No device registered: no push attempt and no log noise")
    void noDevicesNoPush() {
        UUID customer = UUID.randomUUID();
        when(devicePushService.hasDevices(customer)).thenReturn(false);

        consumer(new DeliveredTrigger()).onNotificationEvent(message("parcel.status.changed",
                "{\"customerId\":\"%s\",\"trackingNumber\":\"SD-1\",\"newPublicStatus\":\"DELIVERED\"}".formatted(customer)));

        verify(dispatcher, never()).dispatch(channel("PUSH"));
    }
}
