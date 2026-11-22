package com.swiftdrop.order.service;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.security.JwtHeaderConstants;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * The calling dispatcher's district, from the X-District-Id header the gateway sets from their verified
 * account. Read from the current request so the order service's existing caller arguments (id, role)
 * need not change. Outside a request, or without the header, there is no district.
 */
final class CallerDistrict {

    private CallerDistrict() {
    }

    static SriLankanDistrict current() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        String value = attributes.getRequest().getHeader(JwtHeaderConstants.X_DISTRICT_ID);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return SriLankanDistrict.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
