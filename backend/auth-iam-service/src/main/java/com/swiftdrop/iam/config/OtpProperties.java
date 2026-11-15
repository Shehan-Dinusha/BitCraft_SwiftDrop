package com.swiftdrop.iam.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunables for one-time codes (email verification and password reset), bound from
 * {@code swiftdrop.auth.otp.*}.
 */
@ConfigurationProperties(prefix = "swiftdrop.auth.otp")
public class OtpProperties {

    /** How long a code stays valid. */
    private int ttlMinutes = 10;
    /** Wrong guesses allowed before the code is destroyed and a new one must be requested. */
    private int maxAttempts = 5;
    /** Minimum gap between two codes for the same user and purpose. */
    private int resendCooldownSeconds = 60;
    /** Codes that may be sent per user and purpose within one hour. */
    private int maxSendsPerHour = 5;
    /** Requests per client IP per hour across all OTP endpoints. */
    private int ipMaxRequestsPerHour = 30;
    /** DEV ONLY: write the plaintext code to the log. Must stay false outside local development. */
    private boolean logCode = false;
    /** Never-verified self-service sign-ups are deleted after this many hours. */
    private int unverifiedRetentionHours = 72;

    public int getTtlMinutes() { return ttlMinutes; }
    public void setTtlMinutes(int ttlMinutes) { this.ttlMinutes = ttlMinutes; }

    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }

    public int getResendCooldownSeconds() { return resendCooldownSeconds; }
    public void setResendCooldownSeconds(int resendCooldownSeconds) { this.resendCooldownSeconds = resendCooldownSeconds; }

    public int getMaxSendsPerHour() { return maxSendsPerHour; }
    public void setMaxSendsPerHour(int maxSendsPerHour) { this.maxSendsPerHour = maxSendsPerHour; }

    public int getIpMaxRequestsPerHour() { return ipMaxRequestsPerHour; }
    public void setIpMaxRequestsPerHour(int ipMaxRequestsPerHour) { this.ipMaxRequestsPerHour = ipMaxRequestsPerHour; }

    public boolean isLogCode() { return logCode; }
    public void setLogCode(boolean logCode) { this.logCode = logCode; }

    public int getUnverifiedRetentionHours() { return unverifiedRetentionHours; }
    public void setUnverifiedRetentionHours(int unverifiedRetentionHours) { this.unverifiedRetentionHours = unverifiedRetentionHours; }
}
