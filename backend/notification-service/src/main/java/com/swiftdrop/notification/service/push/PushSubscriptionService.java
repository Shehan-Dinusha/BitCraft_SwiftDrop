package com.swiftdrop.notification.service.push;

import com.swiftdrop.notification.domain.entity.PushSubscription;
import com.swiftdrop.notification.domain.repository.PushSubscriptionRepository;
import com.swiftdrop.notification.util.PiiMasker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Registers and removes the caller's devices. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushSubscriptionService {

    /** Devices kept per user; the oldest is dropped beyond this. */
    static final int MAX_DEVICES_PER_USER = 10;

    /**
     * The server POSTs to the registered endpoint, so only the browsers' real push services are accepted:
     * any other URL would let a caller make this service send requests into the cluster (SSRF).
     */
    static final Set<String> PUSH_SERVICE_HOSTS = Set.of(
            "fcm.googleapis.com",          // Chrome, Edge (Chromium), Android, Samsung Internet
            "push.services.mozilla.com",   // Firefox (updates.push.services.mozilla.com)
            "push.apple.com",              // Safari and iOS home-screen apps (web.push.apple.com)
            "notify.windows.com");         // legacy Edge (*.notify.windows.com)

    private final PushSubscriptionRepository repository;

    /** Saves this device for the caller. A device already known (same endpoint) moves to the caller. */
    @Transactional
    public PushSubscription register(UUID recipientId, String endpoint, String p256dh, String auth, String userAgent) {
        validate(endpoint, p256dh, auth);
        PushSubscription subscription = repository.findByEndpoint(endpoint.trim())
                .orElseGet(() -> PushSubscription.builder().endpoint(endpoint.trim()).build());
        subscription.setRecipientId(recipientId);
        subscription.setP256dh(p256dh.trim());
        subscription.setAuth(auth.trim());
        subscription.setUserAgent(userAgent == null ? null : userAgent.substring(0, Math.min(300, userAgent.length())));
        PushSubscription saved = repository.save(subscription);

        List<PushSubscription> devices = repository.findByRecipientIdOrderByCreatedAtDesc(recipientId);
        if (devices.size() > MAX_DEVICES_PER_USER) {
            repository.deleteAll(devices.subList(MAX_DEVICES_PER_USER, devices.size()));
        }
        log.info("Device push subscription saved for {} ({})", recipientId, PiiMasker.maskEndpoint(endpoint));
        return saved;
    }

    /** Removes the device only when it belongs to the caller. @return whether a device was removed */
    @Transactional
    public boolean remove(UUID recipientId, String endpoint) {
        boolean removed = endpoint != null && repository.deleteByEndpointAndRecipientId(endpoint.trim(), recipientId) > 0;
        if (removed) {
            log.info("Device push subscription removed for {} ({})", recipientId, PiiMasker.maskEndpoint(endpoint));
        }
        return removed;
    }

    static void validate(String endpoint, String p256dh, String auth) {
        URI uri;
        try {
            uri = URI.create(endpoint.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("The push endpoint is not a valid URL");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        boolean knownHost = PUSH_SERVICE_HOSTS.stream().anyMatch(h -> host.equals(h) || host.endsWith("." + h));
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null || !knownHost) {
            throw new IllegalArgumentException("The push endpoint must be an https:// URL of a browser push service");
        }
        try {
            EcKeys.publicKey(EcKeys.decodeBase64Url(p256dh));
            if (EcKeys.decodeBase64Url(auth).length != 16) {
                throw new IllegalArgumentException("auth");
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("The subscription keys are not valid");
        }
    }
}
