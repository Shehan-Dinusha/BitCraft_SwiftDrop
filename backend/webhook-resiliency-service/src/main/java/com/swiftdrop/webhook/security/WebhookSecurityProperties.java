package com.swiftdrop.webhook.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/**
 * Outbound destination policy settings ({@code webhook.security.*}).
 *
 * @param devHttpAllowlist LOCAL DEVELOPMENT ONLY. Exact {@code host:port} entries (for example
 *                         {@code host.docker.internal:9090}) that may be used over plain HTTP and may resolve to a
 *                         private address, so a mock receiver on the developer's machine can be reached. Empty by
 *                         default; wildcards are refused; must be empty when the {@code aks} profile is active
 *                         (startup fails otherwise). Env var: {@code WEBHOOK_SECURITY_DEV_HTTP_ALLOWLIST}
 *                         (comma-separated).
 * @param dnsTimeout       upper bound for resolving a hostname during validation; a timeout rejects the URL
 */
@ConfigurationProperties(prefix = "webhook.security")
public record WebhookSecurityProperties(
        @DefaultValue List<String> devHttpAllowlist,
        @DefaultValue("3s") Duration dnsTimeout
) {
    public WebhookSecurityProperties {
        devHttpAllowlist = devHttpAllowlist == null ? List.of() : List.copyOf(devHttpAllowlist);
        if (dnsTimeout == null || dnsTimeout.isNegative() || dnsTimeout.isZero()) {
            throw new IllegalArgumentException("webhook.security.dns-timeout must be positive");
        }
    }
}
