package com.swiftdrop.pricing.exception;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.pricing.controller.ZoneAdminController;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = ZoneAdminController.class)
public class ZoneAdminExceptionHandler {

    @ExceptionHandler(ZoneNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ZoneNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(ZoneConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(ZoneConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
    }
}
