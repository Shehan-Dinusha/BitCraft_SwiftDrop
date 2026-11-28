package com.swiftdrop.webhook.security;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.spi.InetAddressResolver;
import java.net.spi.InetAddressResolverProvider;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Test-only DNS (JDK {@code InetAddressResolverProvider}, registered in {@code META-INF/services} of the test
 * classpath). Names ending in {@link #SUFFIX} answer from {@link #ANSWERS}; every other name and every reverse lookup
 * goes to the JDK's built-in resolver, so the rest of the test JVM is unaffected.
 *
 * <p>It lets the real {@link BlockingAddressResolverGroup} (which calls {@code InetAddress.getAllByName}) see exactly
 * the addresses a test chooses, with no public DNS. Use each name in one test only: the JDK caches answers.</p>
 */
public class TestDnsResolverProvider extends InetAddressResolverProvider {

    public static final String SUFFIX = ".r3.swiftdrop-test";

    /** Name → addresses. An empty list means "no such host". */
    public static final Map<String, List<InetAddress>> ANSWERS = new ConcurrentHashMap<>();
    /** Lookups served from {@link #ANSWERS}, per name. */
    public static final Map<String, AtomicInteger> LOOKUPS = new ConcurrentHashMap<>();

    /** True once the JDK has loaded this provider (i.e. it really is the resolver in use). */
    public static volatile boolean active;

    @Override
    public InetAddressResolver get(Configuration configuration) {
        active = true;
        InetAddressResolver builtin = configuration.builtinResolver();
        return new InetAddressResolver() {
            @Override
            public Stream<InetAddress> lookupByName(String host, LookupPolicy lookupPolicy) throws UnknownHostException {
                if (host == null || !host.toLowerCase().endsWith(SUFFIX)) {
                    return builtin.lookupByName(host, lookupPolicy);
                }
                String name = host.toLowerCase();
                LOOKUPS.computeIfAbsent(name, n -> new AtomicInteger()).incrementAndGet();
                List<InetAddress> answer = ANSWERS.get(name);
                if (answer == null || answer.isEmpty()) {
                    throw new UnknownHostException(host);
                }
                return answer.stream();
            }

            @Override
            public String lookupByAddress(byte[] addr) throws UnknownHostException {
                return builtin.lookupByAddress(addr);
            }
        };
    }

    @Override
    public String name() {
        return "swiftdrop-r3-test-dns";
    }
}
