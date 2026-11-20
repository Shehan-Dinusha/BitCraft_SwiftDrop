package com.swiftdrop.pricing.security;

import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.security.CallerContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class AdminAccessGuard {

    /**
     * Reads the gateway-verified identity headers and allows only system admins.
     * Used for all zone and tariff write operations. Read endpoints are not guarded.
     */
    public CallerContext requireAdmin(HttpServletRequest request) {
        CallerContext caller = CallerContext.from(request::getHeader);
        caller.requireRole(UserRole.SYSTEM_ADMIN);
        return caller;
    }
}