package com.swiftdrop.webhook.security;

import com.sun.net.httpserver.HttpServer;
import com.swiftdrop.webhook.delivery.AttemptOutcome;
import com.swiftdrop.webhook.delivery.WebhookHttpSender;
import com.swiftdrop.webhook.delivery.WebhookRequest;
import com.swiftdrop.webhook.delivery.WebhookSigner;
import com.swiftdrop.webhook.domain.entity.AttemptErrorClass;
import io.netty.resolver.AddressResolver;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ImmediateEventExecutor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The connect-time SSRF guard ({@link BlockingAddressResolverGroup}) as the HTTP sender uses it. DNS answers come
 * from {@link TestDnsResolverProvider}, so the real resolver code runs against chosen addresses and no public DNS or
 * outside network is involved. Every request that could connect goes to a loopback server owned by this test.
 */
class ConnectTimeAddressGuardTest {

    private static final WebhookUrlPolicy STRICT = policy(List.of());

    private static HttpServer receiver;
    private static final AtomicInteger requests = new AtomicInteger();
    private static int port;

    @BeforeAll
    static void startReceiver() throws Exception {
        receiver = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        receiver.createContext("/hook", exchange -> {
            requests.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        receiver.start();
        port = receiver.getAddress().getPort();
    }

    @AfterAll
    static void stopReceiver() {
        receiver.stop(0);
    }

    private static WebhookUrlPolicy policy(List<String> devAllowlist) {
        return new WebhookUrlPolicy(new WebhookSecurityProperties(devAllowlist, Duration.ofSeconds(1)), new MockEnvironment());
    }

    private static String name(String label, InetAddress... answer) {
        String host = label + "-" + UUID.randomUUID().toString().substring(0, 8) + TestDnsResolverProvider.SUFFIX;
        TestDnsResolverProvider.ANSWERS.put(host, List.of(answer));
        return host;
    }

    private static InetAddress ip(String literal) {
        try {
            return InetAddress.getByName(literal); // literals are parsed, never looked up
        } catch (UnknownHostException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static Future<List<InetSocketAddress>> resolveAll(WebhookUrlPolicy policy, String host, int targetPort) {
        AddressResolver<InetSocketAddress> resolver =
                new BlockingAddressResolverGroup(policy).getResolver(ImmediateEventExecutor.INSTANCE);
        return resolver.resolveAll(InetSocketAddress.createUnresolved(host, targetPort)).awaitUninterruptibly();
    }

    private static Future<InetSocketAddress> resolve(WebhookUrlPolicy policy, InetSocketAddress target) {
        AddressResolver<InetSocketAddress> resolver =
                new BlockingAddressResolverGroup(policy).getResolver(ImmediateEventExecutor.INSTANCE);
        return resolver.resolve(target).awaitUninterruptibly();
    }

    // --- the resolver itself --------------------------------------------------------------------------------------

    @Test
    @DisplayName("the test DNS is the resolver in use (otherwise these tests would prove nothing)")
    void testDnsIsActive() throws Exception {
        String host = name("probe", ip("203.0.114.9"));
        assertThat(InetAddress.getAllByName(host)).containsExactly(ip("203.0.114.9"));
        assertThat(TestDnsResolverProvider.active).isTrue();
    }

    @Test
    @DisplayName("a name with only public addresses resolves to exactly those addresses, port kept")
    void publicAddressesArePermitted() {
        String host = name("public", ip("8.8.8.8"), ip("2606:4700:4700::1111"));

        Future<List<InetSocketAddress>> all = resolveAll(STRICT, host, 8443);
        assertThat(all.isSuccess()).isTrue();
        assertThat(all.getNow()).extracting(InetSocketAddress::getAddress)
                .containsExactly(ip("8.8.8.8"), ip("2606:4700:4700::1111"));
        assertThat(all.getNow()).allSatisfy(address -> assertThat(address.getPort()).isEqualTo(8443));

        Future<InetSocketAddress> one = resolve(STRICT, InetSocketAddress.createUnresolved(host, 8443));
        assertThat(one.isSuccess()).isTrue();
        assertThat(one.getNow().getAddress()).isEqualTo(ip("8.8.8.8"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "127.8.9.10", "::1", "10.1.2.3", "172.16.5.4", "192.168.1.20", "100.64.0.1",
            "169.254.169.254", "fe80::1", "fc00::1", "::ffff:127.0.0.1", "0.0.0.0", "224.0.0.1"})
    @DisplayName("a name resolving to a loopback, private, link-local, metadata or other non-public address is refused")
    void nonPublicAddressesAreBlocked(String literal) {
        String host = name("blocked", ip(literal));

        Future<List<InetSocketAddress>> all = resolveAll(STRICT, host, 443);
        assertThat(all.isSuccess()).isFalse();
        assertThat(all.cause()).isInstanceOf(BlockedDestinationException.class);

        Future<InetSocketAddress> one = resolve(STRICT, InetSocketAddress.createUnresolved(host, 443));
        assertThat(one.cause()).isInstanceOf(BlockedDestinationException.class);
    }

    @Test
    @DisplayName("one blocked address among public ones refuses the whole name, wherever it is in the answer")
    void oneBlockedAddressAmongPublicOnesIsRefused() {
        assertThat(resolveAll(STRICT, name("mixed-last", ip("8.8.8.8"), ip("1.1.1.1"), ip("10.0.0.7")), 443).cause())
                .isInstanceOf(BlockedDestinationException.class);
        assertThat(resolveAll(STRICT, name("mixed-first", ip("169.254.169.254"), ip("8.8.8.8")), 443).cause())
                .isInstanceOf(BlockedDestinationException.class);
        // resolve() would only need the first address; it still checks them all.
        assertThat(resolve(STRICT, InetSocketAddress.createUnresolved(name("mixed-single", ip("8.8.8.8"), ip("::1")), 443))
                .cause()).isInstanceOf(BlockedDestinationException.class);
    }

    @Test
    @DisplayName("a name that does not resolve fails closed (no address is returned)")
    void unresolvableNameFailsClosed() {
        String host = name("missing");

        Future<List<InetSocketAddress>> all = resolveAll(STRICT, host, 443);
        assertThat(all.isSuccess()).isFalse();
        assertThat(all.cause()).isInstanceOf(UnknownHostException.class);
        assertThat(all.getNow()).isNull();
    }

    @Test
    @DisplayName("an address that arrives already resolved (an IP literal) is still checked")
    void alreadyResolvedAddressesAreChecked() throws Exception {
        assertThat(resolve(STRICT, new InetSocketAddress(ip("127.0.0.1"), 443)).cause())
                .isInstanceOf(BlockedDestinationException.class);
        assertThat(resolve(STRICT, new InetSocketAddress(InetAddress.getByAddress("looks-public.example", new byte[]{10, 0, 0, 1}), 443))
                .cause()).isInstanceOf(BlockedDestinationException.class);
        assertThat(resolve(STRICT, new InetSocketAddress(ip("8.8.8.8"), 443)).isSuccess()).isTrue();
    }

    @Test
    @DisplayName("the development exception covers only its exact host and port")
    void devExceptionIsExactHostAndPort() {
        String host = name("dev", ip("127.0.0.1"));
        WebhookUrlPolicy dev = policy(List.of(host + ":9090"));

        assertThat(resolveAll(dev, host, 9090).isSuccess()).isTrue();
        assertThat(resolveAll(dev, host, 9091).cause()).isInstanceOf(BlockedDestinationException.class);
        assertThat(resolveAll(dev, name("other", ip("127.0.0.1")), 9090).cause()).isInstanceOf(BlockedDestinationException.class);
    }

    // --- through the HTTP sender ----------------------------------------------------------------------------------

    private static AttemptOutcome send(WebhookUrlPolicy preCheck, WebhookUrlPolicy connectTime, String url) {
        WebhookHttpSender sender = new WebhookHttpSender(preCheck, new WebhookSigner(),
                new BlockingAddressResolverGroup(connectTime));
        return sender.send(new WebhookRequest(url, "whsec_test", UUID.randomUUID(), UUID.randomUUID(),
                "parcel.status.changed", 1, "{\"id\":\"x\"}".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("a name that passed URL validation but resolves to loopback at connect time is never connected to")
    void rebindingAfterValidationIsBlockedAtConnectTime() {
        String host = name("rebind", ip("127.0.0.1"));
        String url = "http://" + host + ":" + port + "/hook";
        // URL validation passes for this exact host:port (as a public name passes it), so only the connect-time
        // resolver stands between the request and the loopback receiver.
        WebhookUrlPolicy passesValidation = policy(List.of(host + ":" + port));
        int before = requests.get();

        AttemptOutcome outcome = send(passesValidation, STRICT, url);

        assertThat(outcome.errorClass()).isEqualTo(AttemptErrorClass.BLOCKED_DESTINATION);
        assertThat(outcome.httpStatus()).isNull();
        assertThat(outcome.retryable()).isFalse();
        assertThat(requests.get()).isEqualTo(before);
        assertThat(TestDnsResolverProvider.LOOKUPS.get(host)).as("resolved by the connect-time guard").hasValueGreaterThan(0);
    }

    @Test
    @DisplayName("control: with the same URL and a resolver that permits it, the request does reach the receiver")
    void controlTheReceiverIsReachableWhenTheGuardAllowsIt() {
        String host = name("control", ip("127.0.0.1"));
        String url = "http://" + host + ":" + port + "/hook";
        WebhookUrlPolicy allowed = policy(List.of(host + ":" + port));
        int before = requests.get();

        AttemptOutcome outcome = send(allowed, allowed, url);

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.httpStatus()).isEqualTo(200);
        assertThat(requests.get()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("an IP-literal URL that passed validation is refused at connect time, not treated as resolved")
    void ipLiteralUrlIsCheckedAtConnectTime() {
        String url = "http://127.0.0.1:" + port + "/hook";
        int before = requests.get();

        AttemptOutcome outcome = send(policy(List.of("127.0.0.1:" + port)), STRICT, url);

        assertThat(outcome.errorClass()).isEqualTo(AttemptErrorClass.BLOCKED_DESTINATION);
        assertThat(requests.get()).isEqualTo(before);
    }

    @Test
    @DisplayName("a name with no address at connect time is a connection failure, and nothing is sent")
    void unresolvableAtConnectTimeIsAConnectionFailure() {
        String host = name("gone");
        int before = requests.get();

        AttemptOutcome outcome = send(policy(List.of(host + ":" + port)), STRICT, "http://" + host + ":" + port + "/hook");

        assertThat(outcome.errorClass()).isEqualTo(AttemptErrorClass.CONNECTION_FAILURE);
        assertThat(outcome.retryable()).isTrue();
        assertThat(requests.get()).isEqualTo(before);
    }

    @Test
    @DisplayName("the production sender (two-argument constructor) refuses a name resolving to a private address")
    void productionWiringRefusesPrivateNames() {
        String host = name("prod", ip("10.20.30.40"));
        WebhookHttpSender sender = new WebhookHttpSender(STRICT, new WebhookSigner());

        AttemptOutcome outcome = sender.send(new WebhookRequest("https://" + host + "/hook", "whsec_test",
                UUID.randomUUID(), UUID.randomUUID(), "parcel.status.changed", 1, "{}".getBytes(StandardCharsets.UTF_8)));

        assertThat(outcome.errorClass()).isEqualTo(AttemptErrorClass.BLOCKED_DESTINATION);
        assertThat(outcome.retryable()).isFalse();
    }
}
