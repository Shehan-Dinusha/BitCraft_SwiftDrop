package com.swiftdrop.pricing.security;

import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.security.CallerContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class AdminAccessGuard {

    /**
     * Reads the gateway-verified identity headers and allows only dispatchers and system admins.
     */
    public CallerContext requireAdmin(HttpServletRequest request) {
        CallerContext caller = CallerContext.from(request::getHeader);
        caller.requireRole(UserRole.DISPATCHER, UserRole.SYSTEM_ADMIN);
        return caller;
    }
}