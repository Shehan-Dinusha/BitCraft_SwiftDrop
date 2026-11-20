package com.swiftdrop.notification.config;

import com.swiftdrop.notification.service.push.HttpWebPushClient;
import com.swiftdrop.notification.service.push.VapidKeys;
import com.swiftdrop.notification.service.push.WebPushClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Device (Web Push) notifications. Without WEB_PUSH_PUBLIC_KEY, WEB_PUSH_PRIVATE_KEY and WEB_PUSH_SUBJECT the
 * service still starts: device push is skipped and the in-app inbox keeps working, so local development needs
 * no setup. Keys that are present but malformed stop startup, like a misconfigured email provider.
 */
@Slf4j
@Configuration
public class WebPushConfig {

    @Bean
    public WebPushClient webPushClient(
            @Value("${notification.web-push.public-key:}") String publicKey,
            @Value("${notification.web-push.private-key:}") String privateKey,
            @Value("${notification.web-push.subject:}") String subject) {
        return create(publicKey, privateKey, subject);
    }

    static WebPushClient create(String publicKey, String privateKey, String subject) {
        if (isBlank(publicKey) || isBlank(privateKey) || isBlank(subject)) {
            log.info("Device push is off: WEB_PUSH_PUBLIC_KEY, WEB_PUSH_PRIVATE_KEY and WEB_PUSH_SUBJECT are not all set "
                    + "(the in-app inbox still works)");
            return DisabledWebPushClient.INSTANCE;
        }
        try {
            VapidKeys keys = VapidKeys.of(publicKey, privateKey, subject);
            log.info("Device push is on ({})", keys);
            return new HttpWebPushClient(keys);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(e.getMessage() + ". Generate a pair with scripts/generate-helm-secrets.sh.", e);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Used when VAPID keys are not configured. */
    static final class DisabledWebPushClient implements WebPushClient {

        static final DisabledWebPushClient INSTANCE = new DisabledWebPushClient();

        @Override
        public boolean enabled() {
            return false;
        }

        @Override
        public String publicKey() {
            return null;
        }

        @Override
        public int send(String endpoint, byte[] p256dh, byte[] auth, byte[] payload, int ttlSeconds, String urgency, String topic) {
            throw new IllegalStateException("Device push is not configured");
        }
    }
}
