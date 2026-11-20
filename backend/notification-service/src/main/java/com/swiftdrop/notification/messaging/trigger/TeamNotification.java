package com.swiftdrop.notification.messaging.trigger;

/**
 * An in-app notification for a team rather than one user: every member of the audience sees it
 * in their inbox, and its read state is shared. See {@link com.swiftdrop.notification.util.InboxAudience}.
 *
 * @param audience        who sees it, e.g. {@code OPERATIONS} or {@code MERCHANT:<merchantId>}
 * @param notificationType type code that drives the inbox icon, e.g. {@code DISPATCH_FAILED}
 */
public record TeamNotification(String audience, String notificationType, String title, String body,
                               String trackingNumber) {
}
