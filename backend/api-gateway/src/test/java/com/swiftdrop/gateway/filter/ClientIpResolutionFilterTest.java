package com.swiftdrop.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientIpResolutionFilterTest {

    private static final String DEFAULT_TRUSTED =
            "10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,127.0.0.0/8,::1/128,fc00::/7";

    private final ClientIpResolutionFilter filter = new ClientIpResolutionFilter(DEFAULT_TRUSTED);

    /** Runs the filter and returns the request the rest of the chain sees. */
    private ServerWebExchange run(MockServerHttpRequest request) {
        AtomicReference<ServerWebExchange> seen = new AtomicReference<>();
        WebFilterChain chain = exchange -> {
            seen.set(exchange);
            return Mono.empty();
        };
        filter.filter(MockServerWebExchange.from(request), chain).block();
        return seen.get();
    }

    @Test
    void forgedHeaderFromADirectClientIsReplacedByTheRealAddress() {
        ServerWebExchange seen = run(MockServerHttpRequest.get("/api/v1/auth/login")
                .remoteAddress(new InetSocketAddress("203.0.113.7", 51234))
                .header("X-Forwarded-For", "6.6.6.6")
                .header("Forwarded", "for=6.6.6.6")
                .build());

        assertThat(seen.getRequest().getHeaders().get("X-Forwarded-For")).containsExactly("203.0.113.7");
        assertThat(seen.getRequest().getHeaders().getFirst("X-Real-IP")).isEqualTo("203.0.113.7");
        assertThat(seen.getRequest().getHeaders().containsKey("Forwarded")).isFalse();
        assertThat((String) seen.getAttribute(ClientIpResolutionFilter.CLIENT_IP_ATTRIBUTE)).isEqualTo("203.0.113.7");
    }

    @Test
    void clientBehindOurNginxIsTheAddressNginxAppended() {
        // Browser 198.51.100.20 forged "6.6.6.6"; nginx (172.18.0.17) appended the real address.
        ServerWebExchange seen = run(MockServerHttpRequest.get("/api/v1/auth/login")
                .remoteAddress(new InetSocketAddress("172.18.0.17", 40000))
                .header("X-Forwarded-For", "6.6.6.6, 198.51.100.20")
                .build());

        assertThat(seen.getRequest().getHeaders().getFirst("X-Forwarded-For")).isEqualTo("198.51.100.20");
    }

    @Test
    void severalTrustedHopsAreSkipped() {
        // client -> load balancer (10.0.0.4) -> ingress (10.0.1.9) -> gateway
        String resolved = filter.resolve(
                List.of("198.51.100.20, 10.0.0.4"), new InetSocketAddress("10.0.1.9", 1).getAddress());

        assertThat(resolved).isEqualTo("198.51.100.20");
    }

    @Test
    void internalTrafficFallsBackToTheLeftMostHop() {
        String resolved = filter.resolve(
                List.of("10.1.2.3"), new InetSocketAddress("10.0.1.9", 1).getAddress());

        assertThat(resolved).isEqualTo("10.1.2.3");
    }

    @Test
    void multipleHeaderLinesPortsAndBracketsAreHandled() {
        String resolved = filter.resolve(
                List.of("[2001:db8::1]:443", "203.0.113.9:5000, 192.168.1.1"),
                new InetSocketAddress("127.0.0.1", 1).getAddress());

        assertThat(resolved).isEqualTo("203.0.113.9");
    }

    @Test
    void hostnamesAndGarbageAreIgnoredAndNeverResolved() {
        assertThat(ClientIpResolutionFilter.parseLiteral("evil.example.com")).isNull();
        assertThat(ClientIpResolutionFilter.parseLiteral("unknown")).isNull();
        assertThat(ClientIpResolutionFilter.parseLiteral("")).isNull();

        String resolved = filter.resolve(
                List.of("evil.example.com, not-an-ip"), new InetSocketAddress("203.0.113.7", 1).getAddress());
        assertThat(resolved).isEqualTo("203.0.113.7");
    }

    @Test
    void requestWithoutAnyAddressPassesThroughUnchanged() {
        ServerWebExchange seen = run(MockServerHttpRequest.get("/api/v1/orders").build());

        assertThat(seen.getRequest().getHeaders().containsKey("X-Real-IP")).isFalse();
    }

    @Test
    void cidrMatchingRespectsPrefixBoundaries() {
        ClientIpResolutionFilter.Cidr docker = ClientIpResolutionFilter.Cidr.parse("172.16.0.0/12");
        assertThat(docker.contains(ClientIpResolutionFilter.parseLiteral("172.31.255.255"))).isTrue();
        assertThat(docker.contains(ClientIpResolutionFilter.parseLiteral("172.32.0.1"))).isFalse();
        assertThat(docker.contains(ClientIpResolutionFilter.parseLiteral("::1"))).isFalse();

        ClientIpResolutionFilter.Cidr uniqueLocal = ClientIpResolutionFilter.Cidr.parse("fc00::/7");
        assertThat(uniqueLocal.contains(ClientIpResolutionFilter.parseLiteral("fd12::1"))).isTrue();
        assertThat(uniqueLocal.contains(ClientIpResolutionFilter.parseLiteral("2001:db8::1"))).isFalse();
    }

    @Test
    void invalidTrustedProxyConfigurationFailsFast() {
        assertThatThrownBy(() -> new ClientIpResolutionFilter("10.0.0.0/33"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ClientIpResolutionFilter("not-a-range"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
