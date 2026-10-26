package com.swiftdrop.iam.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.contracts.dto.ApiErrorResponse;
import com.swiftdrop.contracts.security.JwtHeaderConstants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        String correlationId = request.getHeader(JwtHeaderConstants.X_CORRELATION_ID);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        if (correlationId != null) {
            response.setHeader(JwtHeaderConstants.X_CORRELATION_ID, correlationId);
        }
        objectMapper.writeValue(response.getOutputStream(), ApiErrorResponse.of(
                HttpServletResponse.SC_UNAUTHORIZED,
                "Unauthorized",
                "Authentication required",
                request.getRequestURI(),
                correlationId
        ));
    }
}
