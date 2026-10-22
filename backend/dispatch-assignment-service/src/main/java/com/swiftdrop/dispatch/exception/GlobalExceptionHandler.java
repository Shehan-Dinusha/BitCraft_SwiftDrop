package com.swiftdrop.dispatch.exception;

import com.swiftdrop.contracts.dto.ApiErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiErrorResponse> handle(RuntimeException ex) {
        return ResponseEntity.badRequest().body(ApiErrorResponse.of(400, "Bad Request", ex.getMessage(), "/api/v1"));
    }
}
