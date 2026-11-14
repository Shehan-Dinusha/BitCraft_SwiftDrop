package com.swiftdrop.webhook.security;

import java.net.UnknownHostException;

/**
 * Raised by {@link BlockingAddressResolverGroup} when the address a webhook connection would use is not a permitted
 * public destination. Extends {@link UnknownHostException} so any code that does not know this type still treats it
 * as a failed resolution (no connection is made). The message is fixed: it never contains the host or the address.
 */
public final class BlockedDestinationException extends UnknownHostException {

    public BlockedDestinationException() {
        super("Destination address is not allowed");
    }
}
