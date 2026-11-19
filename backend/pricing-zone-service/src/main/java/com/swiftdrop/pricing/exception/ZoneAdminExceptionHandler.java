package com.swiftdrop.pricing.exception;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.contracts.security.ForbiddenException;
import com.swiftdrop.contracts.security.UnauthenticatedCallerException;
import com.swiftdrop.pricing.controller.TariffAdminController;
import com.swiftdrop.pricing.controller.ZoneAdminController;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {ZoneAdminController.class, TariffAdminController.class})
public class ZoneAdminExceptionHandler {

    @ExceptionHandler(UnauthenticatedCallerException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthenticated(UnauthenticatedCallerException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden(ForbiddenException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler({ZoneNotFoundException.class, TariffNotFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNotFound(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler({ZoneConflictException.class, TariffConflictException.class})
    public ResponseEntity<ApiResponse<Void>> handleConflict(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
    }
}