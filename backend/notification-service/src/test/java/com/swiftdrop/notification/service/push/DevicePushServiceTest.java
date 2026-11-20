package com.swiftdrop.notification.service.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.notification.domain.entity.PushSubscription;
import com.swiftdrop.notification.domain.repository.PushSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.swiftdrop.notification.service.push.PushSubscriptionServiceTest.AUTH;
import static com.swiftdrop.notification.service.push.PushSubscriptionServiceTest.P256DH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DevicePushServiceTest {

    @Mock
    private PushSubscriptionRepository repository;
    @Mock
    private WebPushClient client;

    private DevicePushService service;
    private final UUID user = UUID.randomUUID();
    private final PushMessages.PushMessage message =
            PushMessages.forTemplate("PARCEL_OUT_FOR_DELIVERY", Map.of("trackingNumber", "SD-1234-5678")).orElseThrow();

    @BeforeEach
    void setUp() {
        service = new DevicePushService(repository, client, new ObjectMapper());
    }

    private PushSubscription device(String endpoint) {
        return PushSubscription.builder().recipientId(user).endpoint(endpoint).p256dh(P256DH).auth(AUTH).build();
    }

    @Test
    @DisplayName("Sends the minimal payload, with urgency, TTL and the tracking number as topic")
    void sendsToEveryDevice() throws Exception {
        PushSubscription phone = device("https://fcm.googleapis.com/fcm/send/phone");
        when(client.enabled()).thenReturn(true);
        when(repository.findByRecipientIdOrderByCreatedAtDesc(user)).thenReturn(List.of(phone));
        when(client.send(any(), any(), any(), any(), anyInt(), any(), any())).thenReturn(201);

        DevicePushService.PushResult result = service.push(user, message);

        assertThat(result.delivered()).isEqualTo(1);
        assertThat(phone.getLastSuccessAt()).isNotNull();
        ArgumentCaptor<byte[]> payload = ArgumentCaptor.forClass(byte[].class);
        verify(client).send(eq(phone.getEndpoint()), any(), any(), payload.capture(),
                eq(PushMessages.CUSTOMER_TTL_SECONDS), eq("normal"), eq("SD-1234-5678"));
        assertThat(new String(payload.getValue(), StandardCharsets.UTF_8)).isEqualTo(
                "{\"title\":\"Out for delivery\",\"body\":\"SD-1234-5678 is on its way\","
                        + "\"url\":\"/customer/tracking/SD-1234-5678\",\"tag\":\"SD-1234-5678\"}");
    }

    @Test
    @DisplayName("404 and 410 delete the subscription; one failing device does not stop the others")
    void cleansUpGoneDevicesAndCarriesOn() throws Exception {
        PushSubscription gone = device("https://fcm.googleapis.com/fcm/send/gone");
        PushSubscription missing = device("https://updates.push.services.mozilla.com/wpush/v2/missing");
        PushSubscription broken = device("https://web.push.apple.com/broken");
        PushSubscription ok = device("https://fcm.googleapis.com/fcm/send/ok");
        when(client.enabled()).thenReturn(true);
        when(repository.findByRecipientIdOrderByCreatedAtDesc(user)).thenReturn(List.of(gone, missing, broken, ok));
        when(client.send(eq(gone.getEndpoint()), any(), any(), any(), anyInt(), any(), any())).thenReturn(410);
        when(client.send(eq(missing.getEndpoint()), any(), any(), any(), anyInt(), any(), any())).thenReturn(404);
        when(client.send(eq(broken.getEndpoint()), any(), any(), any(), anyInt(), any(), any())).thenThrow(new IOException("reset"));
        when(client.send(eq(ok.getEndpoint()), any(), any(), any(), anyInt(), any(), any())).thenReturn(201);

        DevicePushService.PushResult result = service.push(user, message);

        assertThat(result).isEqualTo(new DevicePushService.PushResult(4, 1, 2, 1));
        verify(repository).delete(gone);
        verify(repository).delete(missing);
        verify(repository, never()).delete(broken);
        verify(repository, never()).delete(ok);
    }

    @Test
    @DisplayName("A server error from the push service keeps the subscription")
    void keepsTheDeviceOnServerErrors() throws Exception {
        PushSubscription phone = device("https://fcm.googleapis.com/fcm/send/phone");
        when(client.enabled()).thenReturn(true);
        when(repository.findByRecipientIdOrderByCreatedAtDesc(user)).thenReturn(List.of(phone));
        when(client.send(any(), any(), any(), any(), anyInt(), any(), any())).thenReturn(503);

        assertThat(service.push(user, message).failed()).isEqualTo(1);
        verify(repository, never()).delete(any());
    }

    @Test
    @DisplayName("Without VAPID keys nothing is looked up or sent")
    void disabledDoesNothing() {
        when(client.enabled()).thenReturn(false);

        assertThat(service.hasDevices(user)).isFalse();
        assertThat(service.push(user, message).devices()).isZero();
        verifyNoInteractions(repository);
    }
}
