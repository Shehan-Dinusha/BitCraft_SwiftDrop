package com.swiftdrop.webhook.security;

import com.swiftdrop.webhook.security.UnsafeWebhookUrlException.Reason;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * SSRF guard for merchant webhook target URLs. Applied when an endpoint is registered and again immediately before
 * every outbound request (test ping, legacy replay), so endpoints stored before this policy existed are covered too.
 *
 * <p>Rules, in order. Anything that cannot be validated is rejected (fail closed).</p>
 * <ol>
 *   <li>Absolute {@code http}/{@code https} URL, at most 500 characters, with a syntactically valid host.</li>
 *   <li>No user-info ({@code user:pass@}).</li>
 *   <li>Port, explicit or implied, within 1–65535.</li>
 *   <li>Development exception: an exact {@code host:port} from {@link WebhookSecurityProperties#devHttpAllowlist()}
 *       is accepted over HTTP or HTTPS without the address checks below. Empty by default.</li>
 *   <li>Otherwise HTTPS is required.</li>
 *   <li>IP literals: only canonical dotted-quad IPv4 or bracketed IPv6, and only public addresses. Numeric or hex
 *       host forms such as {@code 2130706433}, {@code 127.1} or {@code 0x7f.0.0.1} are refused.</li>
 *   <li>Hostnames: at least two labels; reserved and internal suffixes ({@code localhost}, {@code .local},
 *       {@code .internal}, {@code .svc}, ...) are refused; the name is resolved and <em>every</em> resolved address must
 *       be public.</li>
 * </ol>
 *
 * <p>Limitation (Step 7): the address is checked here, but the HTTP client resolves the name again when it connects,
 * so a DNS answer that changes between the two (DNS rebinding) is not yet prevented. Step 7 adds a connect-time
 * check in the HTTP client.</p>
 */
@Slf4j
@Component
@EnableConfigurationProperties(WebhookSecurityProperties.class)
public class WebhookUrlPolicy {

    private static final int MAX_URL_LENGTH = 500;

    private static final Pattern CANONICAL_IPV4 = Pattern.compile(
            "^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$");
    /** A final label like this means the host is meant as a number, not a DNS name (no TLD is numeric). */
    private static final Pattern NUMERIC_LABEL = Pattern.compile("^(0x[0-9a-f]*|[0-9]+)$");
    private static final Pattern HOST_LABEL = Pattern.compile("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$");

    private static final Set<String> BLOCKED_SUFFIXES = Set.of(
            "localhost", "localdomain", "local", "internal", "intranet", "lan", "home", "home.arpa", "corp",
            "private", "svc", "cluster.local", "arpa", "test", "invalid", "onion");

    private static final List<Cidr> BLOCKED_IPV4 = List.of(
            Cidr.v4("0.0.0.0/8"),          // "this network", unspecified
            Cidr.v4("10.0.0.0/8"),         // private
            Cidr.v4("100.64.0.0/10"),      // carrier-grade NAT
            Cidr.v4("127.0.0.0/8"),        // loopback
            Cidr.v4("169.254.0.0/16"),     // link-local, cloud metadata (169.254.169.254)
            Cidr.v4("172.16.0.0/12"),      // private
            Cidr.v4("192.0.0.0/24"),       // IETF protocol assignments
            Cidr.v4("192.0.2.0/24"),       // documentation
            Cidr.v4("192.88.99.0/24"),     // 6to4 relay anycast
            Cidr.v4("192.168.0.0/16"),     // private
            Cidr.v4("198.18.0.0/15"),      // benchmarking
            Cidr.v4("198.51.100.0/24"),    // documentation
            Cidr.v4("203.0.113.0/24"),     // documentation
            Cidr.v4("224.0.0.0/4"),        // multicast
            Cidr.v4("240.0.0.0/4"));       // reserved, includes broadcast 255.255.255.255

    private static final List<Cidr> BLOCKED_IPV6 = List.of(
            Cidr.v6("00000000000000000000000000000000", 96), // ::/96: unspecified, loopback ::1, IPv4-compatible
            Cidr.v6("00000000000000000000ffff00000000", 96), // ::ffff:0:0/96 IPv4-mapped
            Cidr.v6("0064ff9b000000000000000000000000", 96), // 64:ff9b::/96 NAT64
            Cidr.v6("0064ff9b000100000000000000000000", 48), // 64:ff9b:1::/48 local NAT64
            Cidr.v6("01000000000000000000000000000000", 64), // 100::/64 discard
            Cidr.v6("20010000000000000000000000000000", 23), // 2001::/23 IETF special purpose (Teredo, ORCHID...)
            Cidr.v6("20010db8000000000000000000000000", 32), // documentation
            Cidr.v6("20020000000000000000000000000000", 16), // 6to4 (embeds IPv4)
            Cidr.v6("fc000000000000000000000000000000", 7),  // unique local
            Cidr.v6("fe800000000000000000000000000000", 10), // link-local
            Cidr.v6("fec00000000000000000000000000000", 10), // site-local (deprecated)
            Cidr.v6("ff000000000000000000000000000000", 8)); // multicast

    private final Set<String> devHttpAllowlist;
    private final Duration dnsTimeout;
    private final ExecutorService resolver = Executors.newVirtualThreadPerTaskExecutor();

    public WebhookUrlPolicy(WebhookSecurityProperties properties, Environment environment) {
        this.dnsTimeout = properties.dnsTimeout();
        this.devHttpAllowlist = properties.devHttpAllowlist().stream()
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .map(WebhookUrlPolicy::parseAllowlistEntry)
                .collect(Collectors.toUnmodifiableSet());
        if (!devHttpAllowlist.isEmpty()) {
            if (environment.acceptsProfiles(Profiles.of("aks"))) {
                throw new IllegalStateException("webhook.security.dev-http-allowlist is a local development setting "
                        + "and must be empty when the 'aks' profile is active");
            }
            log.warn("DEVELOPMENT ONLY: webhook destination policy allows HTTP and private addresses for {}",
                    devHttpAllowlist);
        }
    }

    /** Throws {@link UnsafeWebhookUrlException} with a safe, fixed message if the URL is not an allowed destination. */
    public void validate(String rawUrl) {
        check(rawUrl).ifPresent(reason -> {
            throw new UnsafeWebhookUrlException(reason);
        });
    }

    /** Empty when the URL is an allowed destination; otherwise the reason it is refused. Never throws. */
    public Optional<Reason> check(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank() || rawUrl.length() > MAX_URL_LENGTH) {
            return Optional.of(Reason.MALFORMED);
        }
        URI uri;
        try {
            uri = new URI(rawUrl.trim());
        } catch (URISyntaxException malformed) {
            return Optional.of(Reason.MALFORMED);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return Optional.of(Reason.UNSUPPORTED_SCHEME);
        }
        if (uri.getRawUserInfo() != null || (uri.getRawAuthority() != null && uri.getRawAuthority().contains("@"))) {
            return Optional.of(Reason.USER_INFO);
        }
        if (uri.getHost() == null) {
            // Includes hosts the URI parser could not treat as a server name (underscores, non-ASCII, empty).
            return Optional.of(Reason.MALFORMED);
        }
        int port = uri.getPort() == -1 ? (scheme.equals("https") ? 443 : 80) : uri.getPort();
        if (port < 1 || port > 65535) {
            return Optional.of(Reason.INVALID_PORT);
        }
        String host = normaliseHost(uri.getHost());

        if (devHttpAllowlist.contains(host + ":" + port)) {
            return Optional.empty();
        }
        if (!scheme.equals("https")) {
            return Optional.of(Reason.HTTPS_REQUIRED);
        }
        if (host.startsWith("[")) {
            return checkIpLiteral(host.substring(1, host.length() - 1));
        }
        if (CANONICAL_IPV4.matcher(host).matches()) {
            return checkIpLiteral(host);
        }
        String[] labels = host.split("\\.", -1);
        if (NUMERIC_LABEL.matcher(labels[labels.length - 1]).matches()) {
            return Optional.of(Reason.NON_CANONICAL_IP);
        }
        if (host.length() > 253) {
            return Optional.of(Reason.MALFORMED);
        }
        for (String label : labels) {
            if (!HOST_LABEL.matcher(label).matches()) {
                return Optional.of(Reason.MALFORMED);
            }
        }
        if (labels.length < 2 || isReservedName(host)) {
            return Optional.of(Reason.INTERNAL_HOST);
        }
        return checkResolvedAddresses(host);
    }

    private Optional<Reason> checkIpLiteral(String literal) {
        try {
            // A literal (validated above, or bracketed IPv6) is parsed without a DNS lookup.
            return isBlocked(InetAddress.getByName(literal)) ? Optional.of(Reason.BLOCKED_ADDRESS) : Optional.empty();
        } catch (UnknownHostException | RuntimeException unparseable) {
            return Optional.of(Reason.MALFORMED);
        }
    }

    private Optional<Reason> checkResolvedAddresses(String host) {
        Future<InetAddress[]> lookup = resolver.submit(() -> InetAddress.getAllByName(host));
        InetAddress[] addresses;
        try {
            addresses = lookup.get(dnsTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException timedOut) {
            lookup.cancel(true);
            return Optional.of(Reason.UNRESOLVABLE);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Optional.of(Reason.UNRESOLVABLE);
        } catch (ExecutionException resolutionFailed) {
            return Optional.of(Reason.UNRESOLVABLE);
        }
        if (addresses == null || addresses.length == 0) {
            return Optional.of(Reason.UNRESOLVABLE);
        }
        for (InetAddress address : addresses) {
            if (isBlocked(address)) {
                return Optional.of(Reason.BLOCKED_ADDRESS);
            }
        }
        return Optional.empty();
    }

    /**
     * Whether {@code host:port} is an exact entry of the development allowlist (always false in production, where
     * the list must be empty). Used by the connect-time guard so it honours the same narrow exception.
     */
    public boolean isDevAllowlisted(String host, int port) {
        return host != null && !devHttpAllowlist.isEmpty() && devHttpAllowlist.contains(normaliseHost(host) + ":" + port);
    }

    /** True for any address that is not a permitted public destination (the ranges listed on this class). */
    static boolean isBlocked(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        List<Cidr> ranges = address instanceof Inet4Address ? BLOCKED_IPV4
                : address instanceof Inet6Address ? BLOCKED_IPV6 : null;
        if (ranges == null) {
            return true;
        }
        for (Cidr range : ranges) {
            if (range.contains(bytes)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isReservedName(String host) {
        for (String suffix : BLOCKED_SUFFIXES) {
            if (host.equals(suffix) || host.endsWith("." + suffix)) {
                return true;
            }
        }
        return false;
    }

    private static String normaliseHost(String host) {
        String lower = host.toLowerCase(Locale.ROOT);
        return lower.endsWith(".") ? lower.substring(0, lower.length() - 1) : lower;
    }

    /** {@code host:port}, both required, no wildcards. Invalid entries fail startup rather than being ignored. */
    private static String parseAllowlistEntry(String entry) {
        int colon = entry.lastIndexOf(':');
        if (entry.contains("*") || entry.contains("/") || colon <= 0 || colon == entry.length() - 1) {
            throw new IllegalStateException("webhook.security.dev-http-allowlist entries must be exact host:port values");
        }
        int port;
        try {
            port = Integer.parseInt(entry.substring(colon + 1));
        } catch (NumberFormatException notANumber) {
            throw new IllegalStateException("webhook.security.dev-http-allowlist entries must be exact host:port values");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalStateException("webhook.security.dev-http-allowlist port must be within 1-65535");
        }
        return normaliseHost(entry.substring(0, colon)) + ":" + port;
    }

    private record Cidr(byte[] network, int prefixBits) {

        static Cidr v4(String cidr) {
            String[] parts = cidr.split("/");
            String[] octets = parts[0].split("\\.");
            byte[] network = new byte[4];
            for (int i = 0; i < 4; i++) {
                network[i] = (byte) Integer.parseInt(octets[i]);
            }
            return new Cidr(network, Integer.parseInt(parts[1]));
        }

        static Cidr v6(String hex, int prefixBits) {
            return new Cidr(HexFormat.of().parseHex(hex), prefixBits);
        }

        boolean contains(byte[] address) {
            if (address.length != network.length) {
                return false;
            }
            int fullBytes = prefixBits / 8;
            for (int i = 0; i < fullBytes; i++) {
                if (address[i] != network[i]) {
                    return false;
                }
            }
            int remainingBits = prefixBits % 8;
            if (remainingBits == 0) {
                return true;
            }
            int mask = (0xFF << (8 - remainingBits)) & 0xFF;
            return (address[fullBytes] & mask) == (network[fullBytes] & mask);
        }
    }
}
