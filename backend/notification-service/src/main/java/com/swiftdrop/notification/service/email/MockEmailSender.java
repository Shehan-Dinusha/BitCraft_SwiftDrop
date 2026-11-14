package com.swiftdrop.notification.service.email;

import com.swiftdrop.notification.util.PiiMasker;
import lombok.extern.slf4j.Slf4j;

/** Default provider: records the send in the log only. Used for development, tests and offline demos. */
@Slf4j
public class MockEmailSender implements EmailSender {

    @Override
    public void send(String to, String subject, String plainTextBody, String htmlBody) {
        log.debug("Mock email accepted for {}", PiiMasker.maskEmail(to));
    }

    @Override
    public String providerName() {
        return "mock";
    }
}
