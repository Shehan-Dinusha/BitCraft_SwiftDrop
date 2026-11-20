package com.swiftdrop.notification.service.push;

import com.swiftdrop.notification.domain.entity.PushSubscription;
import com.swiftdrop.notification.domain.repository.PushSubscriptionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PushSubscriptionServiceTest {

    static final String P256DH = "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    static final String AUTH = "BTBZMqHH6r4Tts7J_aSIgg";
    static final String ENDPOINT = "https://fcm.googleapis.com/fcm/send/abc123:APA91b";

    @Mock
    private PushSubscriptionRepository repository;

    @InjectMocks
    private PushSubscriptionService service;

    @Test
    @DisplayName("A new device is saved for the caller")
    void registersANewDevice() {
        UUID user = UUID.randomUUID();
        when(repository.findByEndpoint(ENDPOINT)).thenReturn(Optional.empty());
        when(repository.save(any(PushSubscription.class))).thenAnswer(i -> i.getArgument(0));
        when(repository.findByRecipientIdOrderByCreatedAtDesc(user)).thenReturn(List.of());

        PushSubscription saved = service.register(user, ENDPOINT, P256DH, AUTH, "Mozilla/5.0");

        assertThat(saved.getRecipientId()).isEqualTo(user);
        assertThat(saved.getEndpoint()).isEqualTo(ENDPOINT);
        assertThat(saved.getP256dh()).isEqualTo(P256DH);
    }

    @Test
    @DisplayName("Re-subscribing a known device moves it to the new user (shared phone)")
    void upsertMovesTheDevice() {
        UUID previous = UUID.randomUUID();
        UUID next = UUID.randomUUID();
        PushSubscription existing = PushSubscription.builder().id(5L).recipientId(previous).endpoint(ENDPOINT)
                .p256dh("old").auth("old").build();
        when(repository.findByEndpoint(ENDPOINT)).thenReturn(Optional.of(existing));
        when(repository.save(any(PushSubscription.class))).thenAnswer(i -> i.getArgument(0));
        when(repository.findByRecipientIdOrderByCreatedAtDesc(next)).thenReturn(List.of(existing));

        PushSubscription saved = service.register(next, ENDPOINT, P256DH, AUTH, null);

        assertThat(saved.getId()).isEqualTo(5L);
        assertThat(saved.getRecipientId()).isEqualTo(next);
        assertThat(saved.getAuth()).isEqualTo(AUTH);
    }

    @Test
    @DisplayName("Only the oldest devices beyond the limit are dropped")
    void keepsAtMostTenDevices() {
        UUID user = UUID.randomUUID();
        List<PushSubscription> devices = new ArrayList<>();
        for (int i = 0; i < PushSubscriptionService.MAX_DEVICES_PER_USER + 2; i++) {
            devices.add(PushSubscription.builder().id((long) i).recipientId(user).createdAt(Instant.now().minusSeconds(i)).build());
        }
        when(repository.findByEndpoint(ENDPOINT)).thenReturn(Optional.empty());
        when(repository.save(any(PushSubscription.class))).thenAnswer(i -> i.getArgument(0));
        when(repository.findByRecipientIdOrderByCreatedAtDesc(user)).thenReturn(devices);

        service.register(user, ENDPOINT, P256DH, AUTH, null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PushSubscription>> dropped = ArgumentCaptor.forClass(List.class);
        verify(repository).deleteAll(dropped.capture());
        assertThat(dropped.getValue()).extracting(PushSubscription::getId).containsExactly(10L, 11L);
    }

    @Test
    @DisplayName("Removing only works on the caller's own device")
    void removeIsLimitedToTheOwner() {
        UUID owner = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        when(repository.deleteByEndpointAndRecipientId(ENDPOINT, owner)).thenReturn(1);
        when(repository.deleteByEndpointAndRecipientId(ENDPOINT, stranger)).thenReturn(0);

        assertThat(service.remove(owner, ENDPOINT)).isTrue();
        assertThat(service.remove(stranger, ENDPOINT)).isFalse();
        assertThat(service.remove(owner, null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://fcm.googleapis.com/fcm/send/x",
            "https://updates.push.services.mozilla.com/wpush/v2/x",
            "https://web.push.apple.com/QGuQyavXut",
            "https://wns2-db5p.notify.windows.com/w/?token=x"})
    void acceptsTheBrowsersPushServices(String endpoint) {
        PushSubscriptionService.validate(endpoint, P256DH, AUTH);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://fcm.googleapis.com/fcm/send/x",                 // not https
            "https://notification-service:8088/api/v1/x",           // inside the cluster (SSRF)
            "https://169.254.169.254/latest/meta-data",             // cloud metadata (SSRF)
            "https://fcm.googleapis.com.evil.example/x",            // look-alike host
            "https://evilfcm.googleapis.com.attacker.net/x",
            "https://user:pass@fcm.googleapis.com/x",               // credentials in URL
            "not a url"})
    void refusesAnyOtherEndpoint(String endpoint) {
        assertThatThrownBy(() -> PushSubscriptionService.validate(endpoint, P256DH, AUTH))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void refusesInvalidKeys() {
        assertThatThrownBy(() -> PushSubscriptionService.validate(ENDPOINT, "AAAA", AUTH))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("keys");
        assertThatThrownBy(() -> PushSubscriptionService.validate(ENDPOINT, P256DH, "AAAA"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("keys");
    }
}
