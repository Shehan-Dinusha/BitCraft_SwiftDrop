package com.swiftdrop.notification.util;

import java.util.Set;

/**
 * Templates that carry a secret (a one-time code). For these the pipeline must:
 * <ul>
 *   <li>send even when the user switched email notifications off (security mail is not optional),</li>
 *   <li>store only {@link #REDACTED_CONTENT} in the dispatch audit log, never the rendered body,</li>
 *   <li>never copy the message into the user's in-app inbox.</li>
 * </ul>
 */
public final class SensitiveTemplates {

    public static final String EMAIL_VERIFICATION = "EMAIL_VERIFICATION";
    public static final String PASSWORD_RESET = "PASSWORD_RESET";
    public static final String LOGIN_MFA = "LOGIN_MFA";

    /** What is stored in {@code notification_dispatch_logs.content} instead of the real body. */
    public static final String REDACTED_CONTENT = "[redacted: contains a one-time security code]";

    private static final Set<String> CODES = Set.of(EMAIL_VERIFICATION, PASSWORD_RESET, LOGIN_MFA);

    private SensitiveTemplates() {
    }

    public static boolean isSensitive(String templateCode) {
        return templateCode != null && CODES.contains(templateCode.trim().toUpperCase());
    }
}
