package com.swiftdrop.order.exception;

import com.swiftdrop.contracts.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.HandlerMapping;

import java.util.stream.Collectors;

/**
 * Central error mapping. Error bodies and log lines carry field names, fixed messages and technical
 * identifiers only. They never carry submitted values (addresses, phones, names), raw exception
 * messages, SQL or stack traces, because those can contain personal data or internal details.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final int MAX_CAUSE_DEPTH = 10;
    private static final int MAX_FRAMES = 40;

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(OrderNotFoundException ex, HttpServletRequest request) {
        log.warn("Order not found on {}", safePath(request));
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(404, "Not Found", "Order not found", safePath(request)));
    }

    @ExceptionHandler(InvalidOrderStateException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidState(InvalidOrderStateException ex, HttpServletRequest request) {
        log.warn("Invalid order state transition on {}", safePath(request));
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(409, "Conflict", ex.getMessage(), safePath(request)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        // Field name plus the constraint's fixed message. The rejected value is never read.
        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("Validation error on {}: {}", safePath(request), errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(400, "Validation Failed", errors, safePath(request)));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        // Jackson messages quote the offending value, so only a fixed message is returned or logged.
        log.warn("Unreadable request body on {}", safePath(request));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(400, "Bad Request", "Request body is malformed or contains an invalid value", safePath(request)));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        log.warn("Invalid parameter '{}' on {}", ex.getName(), safePath(request));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(400, "Bad Request", "Invalid value for parameter '" + ex.getName() + "'", safePath(request)));
    }

    @ExceptionHandler(CallerIdentityRequiredException.class)
    public ResponseEntity<ApiErrorResponse> handleCallerIdentityRequired(CallerIdentityRequiredException ex, HttpServletRequest request) {
        log.warn("Caller identity missing on {}", safePath(request));
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiErrorResponse.of(401, "Unauthorized", "Customer identification is required", safePath(request)));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Illegal argument on {}", safePath(request));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(400, "Bad Request", "The request is invalid", safePath(request)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        // The stack trace is kept for debugging, but without exception messages: those can come from the
        // database or libraries and contain row data such as phone numbers.
        log.error("Internal error on {}{}", safePath(request), traceWithoutMessages(ex));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of(500, "Internal Server Error", "An unexpected error occurred", safePath(request)));
    }

    /**
     * The matched route template, such as {@code /api/v1/orders/{orderId}}, instead of the raw URI,
     * so path segments typed by the caller (tracking numbers, ids) are never echoed or logged.
     */
    private String safePath(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return pattern != null ? pattern.toString() : "unmatched";
    }

    /** Exception class names and stack frames for the whole cause chain, never the messages. */
    private String traceWithoutMessages(Throwable throwable) {
        StringBuilder trace = new StringBuilder();
        Throwable current = throwable;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            trace.append(System.lineSeparator()).append(depth == 0 ? "" : "Caused by: ").append(current.getClass().getName());
            StackTraceElement[] frames = current.getStackTrace();
            for (int i = 0; i < Math.min(frames.length, MAX_FRAMES); i++) {
                trace.append(System.lineSeparator()).append("\tat ").append(frames[i]);
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return trace.toString();
    }
}
