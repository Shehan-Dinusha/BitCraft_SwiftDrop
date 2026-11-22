package com.swiftdrop.webhook.security;

import com.swiftdrop.webhook.security.UnsafeWebhookUrlException.Reason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A merchant's URL must never make SwiftDrop call its own network (SSRF). No DNS lookups: literals and names only. */
class WebhookUrlPolicyTest {

    private final WebhookUrlPolicy policy = policy(List.of());

    private static WebhookUrlPolicy policy(List<String> allowlist, String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return new WebhookUrlPolicy(new WebhookSecurityProperties(allowlist, Duration.ofSeconds(1)), environment);
    }

    @ParameterizedTest
    @CsvSource({
            "http://8.8.8.8/hook, HTTPS_REQUIRED",
            "ftp://shop.example.com/hook, UNSUPPORTED_SCHEME",
            "shop.example.com/hook, UNSUPPORTED_SCHEME",
            "https://user:pw@shop.example.com/hook, USER_INFO",
            "https://shop.example.com:70000/hook, INVALID_PORT",
            "https://localhost/hook, INTERNAL_HOST",
            "https://swiftdrop-order.default.svc/hook, INTERNAL_HOST",
            "https://shop.internal/hook, INTERNAL_HOST",
            "https://printer/hook, INTERNAL_HOST",
            "https://2130706433/hook, NON_CANONICAL_IP",
            "https://0x7f.0.0.1/hook, MALFORMED",
            "https://127.1/hook, MALFORMED",
            "https://-bad-.example.com/hook, MALFORMED"
    })
    @DisplayName("Refuses anything but a public HTTPS address, with the reason")
    void refusesUnsafeUrls(String url, Reason reason) {
        assertThat(policy.check(url)).contains(reason);
    }

    @ParameterizedTest
    @ValueSource(strings = {"10.1.2.3", "127.0.0.1", "169.254.169.254", "172.16.0.1", "192.168.1.1", "100.64.0.1",
            "0.0.0.0", "198.18.0.1", "224.0.0.1", "255.255.255.255", "[::1]", "[fe80::1]", "[fc00::1]",
            "[::ffff:127.0.0.1]", "[64:ff9b::7f00:1]", "[2002:7f00:1::]", "[2001:db8::1]"})
    @DisplayName("Refuses private, loopback, metadata and reserved addresses, IPv4 and IPv6")
    void refusesNonPublicAddresses(String host) {
        assertThat(policy.check("https://" + host + "/hook")).contains(Reason.BLOCKED_ADDRESS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"8.8.8.8", "203.0.114.1", "[2606:4700:4700::1111]"})
    @DisplayName("Accepts a public address")
    void acceptsPublicAddresses(String host) {
        assertThat(policy.check("https://" + host + ":8443/hook")).isEmpty();
    }

    @Test
    @DisplayName("Refuses blank, broken and over-long URLs")
    void refusesMalformed() {
        assertThat(policy.check(null)).contains(Reason.MALFORMED);
        assertThat(policy.check("  ")).contains(Reason.MALFORMED);
        assertThat(policy.check("https://shop example.com")).contains(Reason.MALFORMED);
        assertThat(policy.check("https://shop.example.com/" + "a".repeat(500))).contains(Reason.MALFORMED);
        assertThatThrownBy(() -> policy.validate("https://10.0.0.1/secret-path"))
                .isInstanceOf(UnsafeWebhookUrlException.class)
                .hasMessage(Reason.BLOCKED_ADDRESS.message())
                .hasMessageNotContaining("secret-path");
    }

    @Test
    @DisplayName("The development exception is one exact host and port, and never in production")
    void developmentAllowlist() {
        WebhookUrlPolicy dev = policy(List.of("Localhost:9090"));
        assertThat(dev.check("http://localhost:9090/hook")).isEmpty();
        assertThat(dev.check("http://localhost:9091/hook")).contains(Reason.HTTPS_REQUIRED);
        assertThat(dev.isDevAllowlisted("localhost.", 9090)).isTrue();
        assertThat(dev.isDevAllowlisted(null, 9090)).isFalse();

        assertThatThrownBy(() -> policy(List.of("localhost:9090"), "aks")).isInstanceOf(IllegalStateException.class);
        for (String entry : List.of("*:9090", "localhost", "localhost:", "localhost:x", "localhost:70000", "http://a:1")) {
            assertThatThrownBy(() -> policy(List.of(entry))).as(entry).isInstanceOf(IllegalStateException.class);
        }
    }
}
