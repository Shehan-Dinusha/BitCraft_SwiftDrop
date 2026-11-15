package com.swiftdrop.gateway.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Works out the real client address once, at the edge, and hands it downstream in a form that cannot be
 * forged.
 *
 * <p>{@code X-Forwarded-For} is a list the client can pre-fill with anything. Only the entries appended by
 * our own proxies (nginx, an ingress, a load balancer) are trustworthy. So the chain is the header plus
 * the address of whoever connected to the gateway, read <b>right to left</b>, skipping hops inside
 * {@code swiftdrop.gateway.trusted-proxies}. The first address outside that range is the client.
 *
 * <p>The request is then rewritten so that {@code X-Forwarded-For} and {@code X-Real-IP} hold exactly that
 * one address. Every consumer (IAM's login lockout and one-time-code limits, the public tracking rate
 * limiter) therefore sees the same unforgeable value. Before this filter, a client calling the gateway
 * directly could send a new fake IP on every request and bypass all per-IP limits.
 *
 * <p>Runs as a {@link WebFilter} right after {@link SecurityHeadersWebFilter} so it also covers the
 * tracking endpoints that the gateway serves itself.
 */
@Component
public class ClientIpResolutionFilter implements WebFilter, Ordered {

    static final String X_FORWARDED_FOR = "X-Forwarded-For";
    static final String X_REAL_IP = "X-Real-IP";
    static final String FORWARDED = "Forwarded";
    /** Exchange attribute holding the resolved client address. */
    public static final String CLIENT_IP_ATTRIBUTE = ClientIpResolutionFilter.class.getName() + ".clientIp";

    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
    private static final Pattern IPV6 = Pattern.compile("^[0-9A-Fa-f:.]+$");

    private final List<Cidr> trustedProxies;

    public ClientIpResolutionFilter(
            @Value("${swiftdrop.gateway.trusted-proxies:10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,127.0.0.0/8,::1/128,fc00::/7}")
            String trustedProxies) {
        this.trustedProxies = Arrays.stream(trustedProxies.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(Cidr::parse)
                .toList();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String clientIp = resolve(request.getHeaders().get(X_FORWARDED_FOR), remoteAddress(request));
        if (clientIp == null) {
            return chain.filter(exchange);
        }

        ServerHttpRequest sanitized = request.mutate()
                .headers(headers -> {
                    headers.set(X_FORWARDED_FOR, clientIp);
                    headers.set(X_REAL_IP, clientIp);
                    // RFC 7239 header: we don't produce it, so a client-supplied one must not reach services.
                    headers.remove(FORWARDED);
                })
                .build();
        ServerWebExchange mutated = exchange.mutate().request(sanitized).build();
        mutated.getAttributes().put(CLIENT_IP_ATTRIBUTE, clientIp);
        return chain.filter(mutated);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }

    /**
     * @param forwardedFor every {@code X-Forwarded-For} header line (may be null)
     * @param remote       the address that opened the connection to the gateway (may be null)
     * @return the right-most address that is not a trusted proxy; the left-most address when every hop
     *         is trusted (traffic from inside the cluster); null when nothing usable is known
     */
    String resolve(List<String> forwardedFor, InetAddress remote) {
        List<InetAddress> hops = new ArrayList<>();
        if (forwardedFor != null) {
            for (String line : forwardedFor) {
                for (String entry : line.split(",")) {
                    InetAddress address = parseLiteral(entry);
                    if (address != null) {
                        hops.add(address);
                    }
                }
            }
        }
        if (remote != null) {
            hops.add(remote);
        }
        if (hops.isEmpty()) {
            return null;
        }
        for (int i = hops.size() - 1; i >= 0; i--) {
            if (!isTrusted(hops.get(i))) {
                return hops.get(i).getHostAddress();
            }
        }
        return hops.get(0).getHostAddress();
    }

    private boolean isTrusted(InetAddress address) {
        for (Cidr cidr : trustedProxies) {
            if (cidr.contains(address)) {
                return true;
            }
        }
        return false;
    }

    private static InetAddress remoteAddress(ServerHttpRequest request) {
        InetSocketAddress remote = request.getRemoteAddress();
        return remote == null ? null : remote.getAddress();
    }

    /**
     * Parses an IP literal only. Hostnames and garbage are ignored, never resolved: a DNS lookup on a
     * client-controlled string would be both slow and an SSRF-style side channel.
     */
    static InetAddress parseLiteral(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.startsWith("[")) {                       // [2001:db8::1]:443
            int end = value.indexOf(']');
            value = end > 0 ? value.substring(1, end) : "";
        } else if (value.indexOf(':') > 0 && value.indexOf(':') == value.lastIndexOf(':')) {
            value = value.substring(0, value.indexOf(':')); // 203.0.113.7:51234
        }
        boolean literal = IPV4.matcher(value).matches()
                || (value.contains(":") && IPV6.matcher(value).matches());
        if (!literal) {
            return null;
        }
        try {
            return InetAddress.getByName(value);           // literal: no DNS lookup happens
        } catch (UnknownHostException | IllegalArgumentException exception) {
            return null;
        }
    }

    /** An address range such as {@code 10.0.0.0/8} or {@code fc00::/7}. */
    record Cidr(byte[] network, int prefixLength) {

        static Cidr parse(String value) {
            String[] parts = value.split("/", 2);
            InetAddress address = parseLiteral(parts[0]);
            if (address == null) {
                throw new IllegalArgumentException("Invalid trusted proxy range: " + value);
            }
            int maxBits = address.getAddress().length * 8;
            int prefix = parts.length == 2 ? Integer.parseInt(parts[1].trim()) : maxBits;
            if (prefix < 0 || prefix > maxBits) {
                throw new IllegalArgumentException("Invalid prefix length in trusted proxy range: " + value);
            }
            return new Cidr(address.getAddress(), prefix);
        }

        boolean contains(InetAddress candidate) {
            byte[] bytes = candidate.getAddress();
            if (bytes.length != network.length) {
                return false;                               // IPv4 never matches an IPv6 range and vice versa
            }
            int fullBytes = prefixLength / 8;
            for (int i = 0; i < fullBytes; i++) {
                if (bytes[i] != network[i]) {
                    return false;
                }
            }
            int remainingBits = prefixLength % 8;
            if (remainingBits == 0) {
                return true;
            }
            int mask = 0xFF << (8 - remainingBits) & 0xFF;
            return (bytes[fullBytes] & mask) == (network[fullBytes] & mask);
        }
    }
}
