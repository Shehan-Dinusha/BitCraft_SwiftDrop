package com.swiftdrop.analytics.config;

import com.swiftdrop.analytics.security.OperationalReportAccess;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.security.JwtHeaderConstants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.UUID;

@Configuration
public class ReportingAuthorizationConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                if (!(handler instanceof HandlerMethod method)
                        || !(AnnotatedElementUtils.hasAnnotation(method.getMethod(), OperationalReportAccess.class)
                        || AnnotatedElementUtils.hasAnnotation(method.getBeanType(), OperationalReportAccess.class))) {
                    return true;
                }
                String userId = request.getHeader(JwtHeaderConstants.X_USER_ID);
                String role = request.getHeader(JwtHeaderConstants.X_USER_ROLE);
                try {
                    UUID.fromString(userId);
                } catch (IllegalArgumentException | NullPointerException ex) {
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Verified gateway identity required");
                }
                if (role == null || role.isBlank()) {
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Verified gateway role required");
                }
                // Gateway canonicalizes legacy claims; accept only canonical operational roles here.
                if (!UserRole.DISPATCHER.name().equals(role) && !UserRole.SYSTEM_ADMIN.name().equals(role)) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Operational reports require DISPATCHER or SYSTEM_ADMIN");
                }
                return true;
            }
        });
    }
}
