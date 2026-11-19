package com.swiftdrop.notification.service.email;

/**
 * Delivers a rendered notification email. The implementation is chosen by
 * {@code notification.email.provider}: {@code mock} (default) or {@code azure}.
 */
public interface EmailSender {

    /**
     * Sends one email with a plain-text body and an optional HTML body (shown by clients that support it).
     *
     * @throws EmailDeliveryException when the provider rejects or cannot accept the message.
     *         The message never contains the recipient address, credentials or provider response text.
     */
    void send(String to, String subject, String plainTextBody, String htmlBody);

    /** Short provider name for logs, for example {@code mock} or {@code azure}. */
    String providerName();
}
