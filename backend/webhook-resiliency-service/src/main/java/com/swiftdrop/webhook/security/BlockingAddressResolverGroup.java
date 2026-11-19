package com.swiftdrop.webhook.security;

import io.netty.resolver.AbstractAddressResolver;
import io.netty.resolver.AddressResolver;
import io.netty.resolver.AddressResolverGroup;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Promise;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * Connect-time SSRF guard for the webhook HTTP client: the Netty resolver that produces the socket addresses the
 * client actually connects to. Every address it returns has passed {@link WebhookUrlPolicy#isBlocked}, so the
 * connection can only go to an address that was checked in the same lookup. This closes the gap left by validating
 * the URL first and letting the client resolve the name again later (DNS rebinding / time-of-check-time-of-use).
 *
 * <ul>
 *   <li>The name is resolved once (JDK resolver, as {@link WebhookUrlPolicy} does). If <em>any</em> resolved address
 *       is blocked, resolution fails with {@link BlockedDestinationException} and nothing is connected (fail closed).
 *   <li>Addresses that arrive already resolved, such as IP literals, are checked too:
 *       {@link #doIsResolved} always answers false, so nothing bypasses the check.
 *   <li>The only exception is an exact {@code host:port} from the development allowlist
 *       ({@link WebhookUrlPolicy#isDevAllowlisted}), which is empty in production.
 * </ul>
 *
 * <p>Resolution blocks the Netty event-loop thread, exactly as Netty's own {@code DefaultNameResolver} does. That is
 * acceptable for the low, bounded webhook concurrency (decision D9).</p>
 */
public class BlockingAddressResolverGroup extends AddressResolverGroup<InetSocketAddress> {

    private final WebhookUrlPolicy policy;

    public BlockingAddressResolverGroup(WebhookUrlPolicy policy) {
        this.policy = policy;
    }

    @Override
    protected AddressResolver<InetSocketAddress> newResolver(EventExecutor executor) {
        return new CheckingResolver(executor, policy);
    }

    private static final class CheckingResolver extends AbstractAddressResolver<InetSocketAddress> {

        private final WebhookUrlPolicy policy;

        CheckingResolver(EventExecutor executor, WebhookUrlPolicy policy) {
            super(executor, InetSocketAddress.class);
            this.policy = policy;
        }

        /** Never "already resolved": forces every address, including IP literals, through the check. */
        @Override
        protected boolean doIsResolved(InetSocketAddress address) {
            return false;
        }

        @Override
        protected void doResolve(InetSocketAddress unresolved, Promise<InetSocketAddress> promise) {
            try {
                promise.setSuccess(checkedAddresses(unresolved).getFirst());
            } catch (UnknownHostException | RuntimeException refused) {
                promise.setFailure(refused);
            }
        }

        @Override
        protected void doResolveAll(InetSocketAddress unresolved, Promise<List<InetSocketAddress>> promise) {
            try {
                promise.setSuccess(checkedAddresses(unresolved));
            } catch (UnknownHostException | RuntimeException refused) {
                promise.setFailure(refused);
            }
        }

        private List<InetSocketAddress> checkedAddresses(InetSocketAddress target) throws UnknownHostException {
            int port = target.getPort();
            InetAddress[] addresses = target.getAddress() != null
                    ? new InetAddress[]{target.getAddress()}
                    : InetAddress.getAllByName(target.getHostString());
            if (addresses.length == 0) {
                throw new UnknownHostException("No address");
            }
            boolean devException = policy.isDevAllowlisted(target.getHostString(), port);
            List<InetSocketAddress> result = new ArrayList<>(addresses.length);
            for (InetAddress address : addresses) {
                if (!devException && WebhookUrlPolicy.isBlocked(address)) {
                    throw new BlockedDestinationException();
                }
                result.add(new InetSocketAddress(address, port));
            }
            return result;
        }
    }
}
