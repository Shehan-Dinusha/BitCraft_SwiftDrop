package com.swiftdrop.order.service;

import com.swiftdrop.contracts.security.JwtHeaderConstants;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

/** The calling merchant's company, from the X-Merchant-Id header the gateway sets from their verified account. */
final class CallerMerchant {

    private CallerMerchant() {
    }

    static UUID current() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        String value = attributes.getRequest().getHeader(JwtHeaderConstants.X_MERCHANT_ID);
        try {
            return value == null || value.isBlank() ? null : UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
