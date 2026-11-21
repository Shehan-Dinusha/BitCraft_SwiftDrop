package com.swiftdrop.notification.service.push;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.notification.domain.entity.PushSubscription;
import com.swiftdrop.notification.domain.repository.PushSubscriptionRepository;
import com.swiftdrop.notification.service.push.PushMessages.PushMessage;
import com.swiftdrop.notification.util.PiiMasker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Sends a push message to every device of a recipient. One failing device never stops the others. */
@Slf4j
@Service
@RequiredArgsConstructor
public class DevicePushService {

    /** How a send to one recipient went. */
    public record PushResult(int devices, int delivered, int removed, int failed) {
    }

    private final PushSubscriptionRepository repository;
    private final WebPushClient client;
    private final ObjectMapper objectMapper;

    public boolean enabled() {
        return client.enabled();
    }

    /** True when device push is configured and the recipient has at least one device. */
    public boolean hasDevices(UUID recipientId) {
        return client.enabled() && recipientId != null && repository.existsByRecipientId(recipientId);
    }

    public String publicKey() {
        return client.publicKey();
    }

    public PushResult push(UUID recipientId, PushMessage message) {
        if (!client.enabled()) {
            return new PushResult(0, 0, 0, 0);
        }
        byte[] payload = payload(message);
        List<PushSubscription> devices = repository.findByRecipientIdOrderByCreatedAtDesc(recipientId);
        int delivered = 0;
        int removed = 0;
        int failed = 0;
        for (PushSubscription device : devices) {
            String where = PiiMasker.maskEndpoint(device.getEndpoint());
            try {
                int status = client.send(device.getEndpoint(), EcKeys.decodeBase64Url(device.getP256dh()),
                        EcKeys.decodeBase64Url(device.getAuth()), payload, message.ttlSeconds(), message.urgency(), message.tag());
                if (status >= 200 && status < 300) {
                    device.setLastSuccessAt(Instant.now());
                    repository.save(device);
                    delivered++;
                } else if (status == 404 || status == 410) {
                    // The browser unsubscribed or the subscription expired: it will never work again.
                    repository.delete(device);
                    removed++;
                    log.info("Removed expired device subscription of {} ({}, HTTP {})", recipientId, where, status);
                } else {
                    failed++;
                    log.warn("Push service refused a message for {} ({}, HTTP {})", recipientId, where, status);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                failed++;
                break;
            } catch (IOException | RuntimeException e) {
                failed++;
                log.warn("Could not reach the push service for {} ({}): {}", recipientId, where, e.getClass().getSimpleName());
            }
        }
        return new PushResult(devices.size(), delivered, removed, failed);
    }

    private byte[] payload(PushMessage message) {
        Map<String, String> json = new LinkedHashMap<>();
        json.put("title", message.title());
        json.put("body", message.body());
        json.put("url", message.url());
        if (message.tag() != null) {
            json.put("tag", message.tag());
        }
        try {
            return objectMapper.writeValueAsBytes(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not build the push payload", e);
        }
    }
}
