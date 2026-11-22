package com.swiftdrop.analytics.security;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.security.JwtHeaderConstants;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * The district a report covers. A dispatcher always gets their own (from X-District-Id, set by the gateway
 * from their account), whatever the request asks for. A SYSTEM_ADMIN gets the requested one, and
 * {@code null} means the whole island. Callers have passed {@link OperationalReportAccess} already.
 */
public final class ReportDistrict {

    private ReportDistrict() {
    }

    public static SriLankanDistrict of(HttpServletRequest request, SriLankanDistrict requested) {
        if (UserRole.SYSTEM_ADMIN.name().equals(request.getHeader(JwtHeaderConstants.X_USER_ROLE))) {
            return requested;
        }
        String own = request.getHeader(JwtHeaderConstants.X_DISTRICT_ID);
        try {
            return SriLankanDistrict.valueOf(own);
        } catch (IllegalArgumentException | NullPointerException ex) {
            // A dispatcher account always has a district (IAM enforces it); without one, no data.
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Your account has no district");
        }
    }
}
