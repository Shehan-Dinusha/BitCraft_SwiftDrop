package com.swiftdrop.notification.exception;

import com.swiftdrop.contracts.logging.LogSanitizer;
import com.swiftdrop.contracts.dto.ApiErrorResponse;
import com.swiftdrop.contracts.security.CallerContextException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Missing/invalid gateway identity headers -> 401; caller not allowed -> 403. */
    @ExceptionHandler(CallerContextException.class)
    public ResponseEntity<ApiErrorResponse> handleCallerContext(CallerContextException ex, HttpServletRequest request) {
        log.warn("Access denied on {}: {}", LogSanitizer.clean(request.getRequestURI()), LogSanitizer.clean(ex.getMessage()));
        int status = ex.statusCode();
        String errorName = status == 403 ? "Forbidden" : "Unauthorized";
        return ResponseEntity.status(status)
                .body(ApiErrorResponse.of(status, errorName, ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(RecipientPreferenceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handlePreferenceNotFound(
            RecipientPreferenceNotFoundException ex, HttpServletRequest request) {
        log.warn("Recipient preference not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(HttpStatus.NOT_FOUND.value(), "Preference Not Found", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(TemplateNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleTemplateNotFound(
            TemplateNotFoundException ex, HttpServletRequest request) {
        log.warn("Template not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(HttpStatus.NOT_FOUND.value(), "Template Not Found", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("Validation error on {}: {}", LogSanitizer.clean(request.getRequestURI()), LogSanitizer.clean(details));
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of(HttpStatus.BAD_REQUEST.value(), "Validation Failed", details, request.getRequestURI()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Illegal argument: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of(HttpStatus.BAD_REQUEST.value(), "Bad Request", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneral(
            Exception ex, HttpServletRequest request) {
        if (ex instanceof ErrorResponse framework && framework.getStatusCode().is4xxClientError()) {
            return frameworkClientError(framework, request.getRequestURI());
        }
        log.error("Unhandled notification service exception: ", ex);
        // Never return the raw exception message: it can carry database or library details.
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error", "An unexpected error occurred", request.getRequestURI()));
    }

    /**
     * Spring MVC's own client errors (unknown path 404, wrong method 405, missing parameter 400,
     * unsupported media type 415, ...) implement {@link ErrorResponse} and already carry the right
     * status. Return it instead of letting the catch-all turn them into a 500.
     */
    private static ResponseEntity<ApiErrorResponse> frameworkClientError(ErrorResponse error, String path) {
        HttpStatusCode status = error.getStatusCode();
        HttpStatus known = HttpStatus.resolve(status.value());
        String reason = known != null ? known.getReasonPhrase() : "Client Error";
        return ResponseEntity.status(status).headers(error.getHeaders())
                .body(ApiErrorResponse.of(status.value(), reason, reason, path));
    }
}
