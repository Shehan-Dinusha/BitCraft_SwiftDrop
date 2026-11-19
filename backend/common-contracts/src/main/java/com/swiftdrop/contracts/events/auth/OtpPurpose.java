package com.swiftdrop.contracts.events.auth;

/** Why a one-time code was issued. Each purpose maps to its own email template. */
public enum OtpPurpose {
    EMAIL_VERIFICATION,
    PASSWORD_RESET,
    /** Second sign-in factor for privileged roles (SYSTEM_ADMIN, DISPATCHER). */
    LOGIN_MFA
}
