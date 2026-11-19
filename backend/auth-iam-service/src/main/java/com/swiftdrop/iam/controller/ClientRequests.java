package com.swiftdrop.iam.controller;

import com.swiftdrop.contracts.security.JwtHeaderConstants;
import jakarta.servlet.http.HttpServletRequest;

/** Request details shared by the auth controllers (client IP for throttling, bearer token). */
final class ClientRequests {

    private ClientRequests() {
    }

    /**
     * The client's IP as seen by the gateway. The gateway appends the address it received the
     * request from, so the rightmost X-Forwarded-For entry is the trustworthy one; earlier entries
     * are whatever the client chose to send.
     */
    static String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String[] hops = forwardedFor.split(",");
            return hops[hops.length - 1].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    static String userAgent(HttpServletRequest request) {
        return request.getHeader("User-Agent");
    }

    /** The raw token from an {@code Authorization: Bearer ...} header, or {@code null}. */
    static String bearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith(JwtHeaderConstants.BEARER_PREFIX)) {
            return null;
        }
        return authorization.substring(JwtHeaderConstants.BEARER_PREFIX.length()).trim();
    }
}
