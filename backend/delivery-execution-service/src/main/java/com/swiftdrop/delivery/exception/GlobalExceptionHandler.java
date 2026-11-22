package com.swiftdrop.delivery.exception;

import com.swiftdrop.contracts.logging.LogSanitizer;
import com.swiftdrop.contracts.dto.ApiErrorResponse;
import com.swiftdrop.contracts.security.CallerContextException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.sql.SQLException;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DeliveryNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(DeliveryNotFoundException ex, HttpServletRequest request) {
        log.warn("Delivery not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(404, "Not Found", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidTransition(InvalidStateTransitionException ex, HttpServletRequest request) {
        log.warn("Invalid delivery FSM transition: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(409, "Invalid State Transition", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(CashConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleCashConflict(CashConflictException ex, HttpServletRequest request) {
        log.warn("Cash step rejected on {}", LogSanitizer.clean(request.getRequestURI()));
        return conflict("Cash Conflict", ex.getMessage(), request);
    }

    @ExceptionHandler(CashRecordNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleCashNotFound(CashRecordNotFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(404, "Not Found", ex.getMessage(), request.getRequestURI()));
    }

    /** Two people changed the same cash record, or collected the same cash, at the same time. */
    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, DuplicateKeyException.class})
    public ResponseEntity<ApiErrorResponse> handleConcurrentChange(Exception ex, HttpServletRequest request) {
        log.warn("Concurrent change rejected on {}", LogSanitizer.clean(request.getRequestURI()));
        return conflict("Conflict", "This was changed by someone else. Refresh and try again.", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
                return handleConcurrentChange(ex, request);
            }
        }
        return handleGeneric(ex, request);
    }

    private static ResponseEntity<ApiErrorResponse> conflict(String error, String message, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(409, error, message, request.getRequestURI()));
    }

    @ExceptionHandler(OrderDetailsUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleOrderDetailsUnavailable(OrderDetailsUnavailableException ex, HttpServletRequest request) {
        log.warn("Order details unavailable on {}", LogSanitizer.clean(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiErrorResponse.of(503, "Service Unavailable", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(CallerContextException.class)
    public ResponseEntity<ApiErrorResponse> handleCallerContext(CallerContextException ex, HttpServletRequest request) {
        log.warn("Security authorisation exception on {}: {}", LogSanitizer.clean(request.getRequestURI()), LogSanitizer.clean(ex.getMessage()));
        int status = ex.statusCode();
        String errorName = status == 403 ? "Forbidden" : "Unauthorized";
        return ResponseEntity.status(status)
                .body(ApiErrorResponse.of(status, errorName, ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("Validation error on {}: {}", LogSanitizer.clean(request.getRequestURI()), LogSanitizer.clean(errors));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(400, "Validation Failed", errors, request.getRequestURI()));
    }

    /** A malformed id, date or district in the URL. Spring does not mark this one as a client error. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiErrorResponse.of(400, "Bad Request",
                "Invalid value for " + ex.getName(), request.getRequestURI()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Illegal argument on {}: {}", LogSanitizer.clean(request.getRequestURI()), LogSanitizer.clean(ex.getMessage()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(400, "Bad Request", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        if (ex instanceof ErrorResponse framework && framework.getStatusCode().is4xxClientError()) {
            return frameworkClientError(framework, request.getRequestURI());
        }
        log.error("Internal error on {}", LogSanitizer.clean(request.getRequestURI()), ex);
        // Never return the raw exception message: it can carry database or library details.
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of(500, "Internal Server Error", "An unexpected error occurred", request.getRequestURI()));
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
