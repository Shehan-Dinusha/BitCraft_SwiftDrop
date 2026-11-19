package com.swiftdrop.notification.util;

import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.security.CallerContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Team inboxes. Besides their own notifications, users see the team notifications for their audiences:
 * <ul>
 *   <li>{@code OPERATIONS}: every DISPATCHER and SYSTEM_ADMIN (for example "no courier could be assigned");</li>
 *   <li>{@code MERCHANT:<merchantId>}: every user of that merchant company (for example "new order received").</li>
 * </ul>
 * A team notification's read state is shared by the team.
 */
public final class InboxAudience {

    public static final String OPERATIONS = "OPERATIONS";
    private static final String MERCHANT_PREFIX = "MERCHANT:";

    private InboxAudience() {
    }

    public static String merchant(UUID merchantId) {
        return merchantId == null ? null : MERCHANT_PREFIX + merchantId;
    }

    /** Team audiences the caller belongs to (possibly none). */
    public static List<String> forCaller(CallerContext caller) {
        List<String> audiences = new ArrayList<>();
        if (caller.role() == UserRole.DISPATCHER || caller.role() == UserRole.SYSTEM_ADMIN) {
            audiences.add(OPERATIONS);
        }
        if (caller.role() == UserRole.MERCHANT && caller.merchantId() != null) {
            audiences.add(merchant(caller.merchantId()));
        }
        return audiences;
    }
}
