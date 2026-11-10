package com.swiftdrop.analytics.exception;

import com.swiftdrop.contracts.dto.ApiErrorResponse;
import com.swiftdrop.contracts.security.JwtHeaderConstants;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.validation.BindException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> status(ResponseStatusException ex, HttpServletRequest request) {
        var status = HttpStatus.valueOf(ex.getStatusCode().value());
        return error(status, ex.getReason(), request);
    }

    @ExceptionHandler({BindException.class, ServletRequestBindingException.class,
            MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiErrorResponse> invalid(Exception ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "Invalid request. Reporting dates must use yyyy-MM-dd and span 1 to 90 inclusive days", request);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiErrorResponse> handle(RuntimeException ex, HttpServletRequest request) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to process request", request);
    }

    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(ApiErrorResponse.of(status.value(), status.getReasonPhrase(), message,
                request.getRequestURI(), request.getHeader(JwtHeaderConstants.X_CORRELATION_ID)));
    }
}
