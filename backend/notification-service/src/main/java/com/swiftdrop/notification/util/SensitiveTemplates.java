package com.swiftdrop.notification.util;

import java.util.Locale;
import java.util.Set;

/**
 * Account-security templates.
 *
 * <p>Every security template ({@link #isSecurity}) is sent even when the user switched email
 * notifications off, goes only to the account's registered (IAM) address, is never copied to the
 * in-app inbox and cannot be sent through manual dispatch.
 *
 * <p>One-time-code templates ({@link #isSensitive}) additionally carry a secret, so the dispatch
 * audit log stores only {@link #REDACTED_CONTENT} for them. Sign-in alerts carry no secret.
 */
public final class SensitiveTemplates {

    public static final String EMAIL_VERIFICATION = "EMAIL_VERIFICATION";
    public static final String PASSWORD_RESET = "PASSWORD_RESET";
    public static final String LOGIN_MFA = "LOGIN_MFA";

    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String LOGIN_FAILED = "LOGIN_FAILED";

    /** What is stored in {@code notification_dispatch_logs.content} instead of the real body. */
    public static final String REDACTED_CONTENT = "[redacted: contains a one-time security code]";

    private static final Set<String> CODES = Set.of(EMAIL_VERIFICATION, PASSWORD_RESET, LOGIN_MFA);
    private static final Set<String> ALERTS = Set.of(LOGIN_SUCCESS, LOGIN_FAILED);

    private SensitiveTemplates() {
    }

    /** Carries a one-time code: never stored in logs. */
    public static boolean isSensitive(String templateCode) {
        return CODES.contains(normalise(templateCode));
    }

    /** Any account-security email (one-time codes and sign-in alerts). */
    public static boolean isSecurity(String templateCode) {
        String code = normalise(templateCode);
        return CODES.contains(code) || ALERTS.contains(code);
    }

    private static String normalise(String templateCode) {
        return templateCode == null ? "" : templateCode.trim().toUpperCase(Locale.ROOT);
    }
}
